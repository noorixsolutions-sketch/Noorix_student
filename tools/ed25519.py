"""Ed25519 (RFC 8032) — Python reference implementation (RFC 8032, Section 6).

Used OFFLINE by tools/sign_course_package.py so the Noorix publisher
private key never ships in the Android APK. Self-test runs the RFC 8032
Section 7.1 test vectors (1, 2, 3) on every invocation.
"""
import hashlib


def sha512(s: bytes) -> bytes:
    return hashlib.sha512(s).digest()


# Base field Z_p
p = 2 ** 255 - 19


def modp_inv(x):
    return pow(x, p - 2, p)


# Curve constant
d = -121665 * modp_inv(121666) % p

# Group order
q = 2 ** 252 + 27742317777372353535851937790883648493


def sha512_modq(s):
    return int.from_bytes(sha512(s), "little") % q


# Points are represented as tuples (X, Y, Z, T) of extended coordinates,
# with x = X/Z, y = Y/Z, x*y = T/Z


def point_add(P, Q):
    A, B = (P[1] - P[0]) * (Q[1] - Q[0]) % p, (P[1] + P[0]) * (Q[1] + Q[0]) % p
    C, D = 2 * P[3] * Q[3] * d % p, 2 * P[2] * Q[2] % p
    E, F, G, H = B - A, D - C, D + C, B + A
    return (E * F, G * H, F * G, E * H)


def point_mul(s, P):
    Q = (0, 1, 1, 0)  # Neutral element
    while s > 0:
        if s & 1:
            Q = point_add(Q, P)
        P = point_add(P, P)
        s >>= 1
    return Q


def point_equal(P, Q):
    if (P[0] * Q[2] - Q[0] * P[2]) % p != 0:
        return False
    if (P[1] * Q[2] - Q[1] * P[2]) % p != 0:
        return False
    return True


# Square root of -1
modp_sqrt_m1 = pow(2, (p - 1) // 4, p)


def recover_x(y, sign):
    if y >= p:
        return None
    x2 = (y * y - 1) * modp_inv(d * y * y + 1)
    if x2 == 0:
        if sign:
            return None
        else:
            return 0
    x = pow(x2, (p + 3) // 8, p)
    if (x * x - x2) % p != 0:
        x = x * modp_sqrt_m1 % p
    if (x * x - x2) % p != 0:
        return None
    if (x & 1) != sign:
        x = p - x
    return x


# Base point
g_y = 4 * modp_inv(5) % p
g_x = recover_x(g_y, 0)
G = (g_x, g_y, 1, g_x * g_y % p)


def point_compress(P):
    zinv = modp_inv(P[2])
    x = P[0] * zinv % p
    y = P[1] * zinv % p
    return int.to_bytes(y | ((x & 1) << 255), 32, "little")


def point_decompress(s):
    if len(s) != 32:
        raise ValueError("Invalid input length for decompression")
    y = int.from_bytes(s, "little")
    sign = y >> 255
    y &= (1 << 255) - 1
    x = recover_x(y, sign)
    if x is None:
        return None
    return (x, y, 1, x * y % p)


def secret_expand(secret):
    if len(secret) != 32:
        raise ValueError("Bad size of private key")
    h = sha512(secret)
    a = int.from_bytes(h[:32], "little")
    a &= (1 << 254) - 8
    a |= (1 << 254)
    return (a, h[32:])


def public_key_from_seed(secret: bytes) -> bytes:
    (a, _dummy) = secret_expand(secret)
    return point_compress(point_mul(a, G))


def sign(secret: bytes, msg: bytes) -> bytes:
    a, prefix = secret_expand(secret)
    A = point_compress(point_mul(a, G))
    r = sha512_modq(prefix + msg)
    R = point_mul(r, G)
    Rs = point_compress(R)
    h = sha512_modq(Rs + A + msg)
    s = (r + h * a) % q
    return Rs + int.to_bytes(s, 32, "little")


def verify(public: bytes, msg: bytes, signature: bytes) -> bool:
    try:
        if len(public) != 32:
            return False
        if len(signature) != 64:
            return False
        A = point_decompress(public)
        if not A:
            return False
        Rs = signature[:32]
        R = point_decompress(Rs)
        if not R:
            return False
        s = int.from_bytes(signature[32:], "little")
        if s >= q:
            return False
        h = sha512_modq(Rs + public + msg)
        sB = point_mul(s, G)
        hA = point_mul(h, A)
        return point_equal(sB, point_add(R, hA))
    except Exception:
        return False


def self_test() -> None:
    # RFC 8032, Section 7.1 — TEST 1 (empty message)
    sk1 = bytes.fromhex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
    pk1 = bytes.fromhex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
    sig1 = bytes.fromhex(
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
        "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
    )
    assert public_key_from_seed(sk1) == pk1, "TEST 1: public key mismatch"
    assert sign(sk1, b"") == sig1, "TEST 1: signature mismatch"
    assert verify(pk1, b"", sig1) is True, "TEST 1: verify failed"

    # RFC 8032, Section 7.1 — TEST 2 (message "r")
    sk2 = bytes.fromhex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb")
    pk2 = bytes.fromhex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
    sig2 = bytes.fromhex(
        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da"
        "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"
    )
    assert public_key_from_seed(sk2) == pk2, "TEST 2: public key mismatch"
    assert sign(sk2, b"\x72") == sig2, "TEST 2: signature mismatch"
    assert verify(pk2, b"\x72", sig2) is True, "TEST 2: verify failed"

    # RFC 8032, Section 7.1 — TEST 3 (message "af82")
    sk3 = bytes.fromhex("c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7")
    pk3 = bytes.fromhex("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025")
    sig3 = bytes.fromhex(
        "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac"
        "18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a"
    )
    assert public_key_from_seed(sk3) == pk3, "TEST 3: public key mismatch"
    assert sign(sk3, bytes.fromhex("af82")) == sig3, "TEST 3: signature mismatch"
    assert verify(pk3, bytes.fromhex("af82"), sig3) is True, "TEST 3: verify failed"

    # Negative cases
    assert verify(pk1, b"tampered", sig1) is False
    bad = bytearray(sig1)
    bad[20] ^= 8
    assert verify(pk1, b"", bytes(bad)) is False
    print("ed25519 self_test: OK (RFC 8032 Section 7.1 TEST 1-3 + negative cases)")


if __name__ == "__main__":
    self_test()
