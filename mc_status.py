#!/usr/bin/env python3
"""
Minecraft Server Status Protocol - Capture raw JSON response
Test multiple protocol versions to find head MOTD
"""
import socket
import struct
import json
import os

def create_varint(val):
    result = b""
    while True:
        byte = val & 0x7F
        val >>= 7
        if val:
            byte |= 0x80
        result += struct.pack("B", byte)
        if not val:
            break
    return result

def read_varint(sock):
    result = 0
    num_read = 0
    while True:
        data = sock.recv(1)
        if not data:
            raise ConnectionError("Connection closed")
        byte = data[0]
        result |= (byte & 0x7F) << (7 * num_read)
        num_read += 1
        if not (byte & 0x80):
            break
        if num_read > 5:
            raise ValueError("VarInt too big")
    return result

def get_server_status(host, port=25565, protocol_version=770):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(10)
    
    try:
        resolved = socket.getaddrinfo(host, port, socket.AF_INET, socket.SOCK_STREAM)
        addr = resolved[0][4]
        print(f"Connecting to {host}:{port} (protocol={protocol_version})...")
        sock.connect(addr)
        
        host_bytes = host.encode('utf-8')
        handshake_data = (
            create_varint(0x00) +
            create_varint(protocol_version) +
            create_varint(len(host_bytes)) +
            host_bytes +
            struct.pack('>H', port) +
            create_varint(1)
        )
        sock.send(create_varint(len(handshake_data)) + handshake_data)
        
        request_data = create_varint(0x00)
        sock.send(create_varint(len(request_data)) + request_data)
        
        packet_length = read_varint(sock)
        packet_id = read_varint(sock)
        json_length = read_varint(sock)
        
        json_data = b""
        while len(json_data) < json_length:
            chunk = sock.recv(min(4096, json_length - len(json_data)))
            if not chunk:
                break
            json_data += chunk
        
        return json_data.decode('utf-8')
        
    finally:
        sock.close()

if __name__ == "__main__":
    host = "play.zedar.xyz"
    port = 25565
    
    # Test multiple protocol versions
    protocols = [770, 773, 775, 780, 790, 800]
    
    for pv in protocols:
        print(f"\n{'='*60}")
        print(f"Protocol Version: {pv}")
        print(f"{'='*60}")
        try:
            raw_json = get_server_status(host, port, protocol_version=pv)
            parsed = json.loads(raw_json)
            
            outdir = os.environ.get("TEMP", ".")
            filename = os.path.join(outdir, f"zedarmc_pv{pv}.json")
            with open(filename, 'w', encoding='utf-8') as f:
                json.dump(parsed, f, indent=2, ensure_ascii=False)
            print(f"Saved to: {filename}")
            
            if "description" in parsed:
                desc = parsed["description"]
                has_heads = False
                head_count = 0
                
                if isinstance(desc, dict) and "extra" in desc:
                    extras = desc["extra"]
                    for e in extras:
                        if isinstance(e, dict) and "player" in e:
                            has_heads = True
                            head_count += 1
                        # Also check nested extras
                        if isinstance(e, dict) and "extra" in e:
                            for sub in e["extra"]:
                                if isinstance(sub, dict) and "player" in sub:
                                    has_heads = True
                                    head_count += 1
                
                if has_heads:
                    print(f"*** FOUND HEAD COMPONENTS! Count: {head_count} ***")
                    # Show one example
                    for e in extras:
                        if isinstance(e, dict) and "player" in e:
                            print(f"Example head: {json.dumps(e, indent=2)[:500]}")
                            break
                else:
                    # Show first 200 chars of description
                    desc_str = json.dumps(desc, ensure_ascii=False)
                    print(f"Text MOTD (no heads): {desc_str[:200]}...")
            
            if "version" in parsed:
                print(f"Version: {json.dumps(parsed['version'])}")
                
        except Exception as e:
            print(f"Error with protocol {pv}: {e}")
