"""Write an empty structure template (air box) for GameTests: data/utspike/structures/empty.nbt."""
import gzip, struct, sys

def s(name):
    b = name.encode(); return struct.pack('>H', len(b)) + b

def tag_int_list(name, vals):
    return b'\x09' + s(name) + b'\x03' + struct.pack('>i', len(vals)) + b''.join(struct.pack('>i', v) for v in vals)

def tag_empty_list(name):
    return b'\x09' + s(name) + b'\x00' + struct.pack('>i', 0)

w, h, d = (int(x) for x in sys.argv[2:5])
body = (tag_int_list('size', [w, h, d]) + tag_empty_list('blocks') + tag_empty_list('palette')
        + tag_empty_list('entities') + b'\x03' + s('DataVersion') + struct.pack('>i', 3465))
root = b'\x0a' + s('') + body + b'\x00'
with gzip.open(sys.argv[1], 'wb') as f:
    f.write(root)
