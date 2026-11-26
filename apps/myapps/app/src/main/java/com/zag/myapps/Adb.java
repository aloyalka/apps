package com.zag.myapps;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;

/**
 * A minimal ADB client that talks to the Fire TV's own debugging service on 127.0.0.1:5555.
 * The first connection makes the TV ask "Allow USB debugging?"; once allowed, commands run
 * with shell rights (force-stop, granting the setup permission, sleep).
 */
final class Adb {
    private Adb() {}

    private static final int CNXN = 0x4e584e43;
    private static final int AUTH = 0x48545541;
    private static final int OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f;
    private static final int CLSE = 0x45534c43;
    private static final int WRTE = 0x45545257;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final int VERSION = 0x01000000;
    private static final int MAX_DATA = 4096;
    private static final int KEY_WORDS = 64; // 2048-bit key

    /** PKCS#1 v1.5 type-1 padding plus the SHA-1 DigestInfo prefix, sized for a 2048-bit key. */
    private static final byte[] SIG_PADDING;
    static {
        byte[] prefix = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
        SIG_PADDING = new byte[256 - 20];
        SIG_PADDING[0] = 0x00;
        SIG_PADDING[1] = 0x01;
        int ffEnd = SIG_PADDING.length - prefix.length - 1;
        for (int i = 2; i < ffEnd; i++) SIG_PADDING[i] = (byte) 0xff;
        SIG_PADDING[ffEnd] = 0x00;
        System.arraycopy(prefix, 0, SIG_PADDING, ffEnd + 1, prefix.length);
    }

    private static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    /** Thrown when retrying can't help (nothing listening, or the person didn't answer the prompt). */
    private static final class FinalError extends IOException {
        final boolean stopNow;
        FinalError(String m) { this(m, false); }
        FinalError(String m, boolean stopNow) { super(m); this.stopNow = stopNow; }
    }

    /**
     * Runs one shell command and returns its output. Call off the main thread.
     * Tries the TV's internal address and then its Wi-Fi address, up to three rounds,
     * because some Fire OS builds drop the first connection or treat the two differently.
     */
    static synchronized String shell(Context c, String command) throws IOException {
        KeyPair kp = keys(c);
        List<String> hosts = hosts();
        IOException last = null;
        for (int round = 0; round < 3; round++) {
            for (String host : hosts) {
                try {
                    return shellOnce(kp, host, command);
                } catch (FinalError e) {
                    if (e.stopNow) throw e;
                    last = e;
                } catch (IOException e) {
                    last = e;
                }
            }
            if (last instanceof FinalError && round > 0) break;
            try { Thread.sleep(2500); } catch (InterruptedException ie) { break; }
        }
        throw last != null ? last : new IOException("Couldn't connect.");
    }

    private static List<String> hosts() {
        List<String> out = new ArrayList<>();
        out.add("127.0.0.1");
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String shellOnce(KeyPair kp, String host, String command) throws IOException {
        String stage = "connecting to " + host;
        try (Socket s = new Socket()) {
            try {
                s.connect(new InetSocketAddress(host, 5555), 3000);
            } catch (IOException e) {
                throw new FinalError("Can't reach ADB debugging at " + host
                        + ". Check Settings > My Fire TV > Developer options > ADB debugging.");
            }
            // Long timeout while the TV may be waiting for "Allow USB debugging?".
            s.setSoTimeout(120000);
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            stage = "handshake with " + host;
            send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
            boolean sentSig = false, sentKey = false;
            while (true) {
                Msg m;
                try {
                    m = read(in);
                } catch (SocketTimeoutException e) {
                    throw new FinalError("No answer to “Allow USB debugging?”. Run setup again and press OK on the TV.", true);
                }
                if (m.cmd == CNXN) break;
                if (m.cmd == AUTH && m.arg0 == AUTH_TOKEN) {
                    if (!sentSig) {
                        stage = "signing in to " + host;
                        send(out, AUTH, AUTH_SIGNATURE, 0, sign(kp.getPrivate(), m.data));
                        sentSig = true;
                    } else if (!sentKey) {
                        stage = "waiting for “Allow USB debugging?” on the TV (" + host + ")";
                        send(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBytes((RSAPublicKey) kp.getPublic()));
                        sentKey = true;
                    } else {
                        throw new FinalError("The TV didn't allow debugging access.", true);
                    }
                } else {
                    throw new IOException("Unexpected reply from ADB at " + host + ".");
                }
            }

            stage = "running a command on " + host;
            s.setSoTimeout(30000);
            int localId = 1;
            send(out, OPEN, localId, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            while (true) {
                Msg m = read(in);
                if (m.cmd == OKAY) continue;
                if (m.cmd == WRTE) {
                    result.write(m.data);
                    send(out, OKAY, localId, m.arg0, new byte[0]);
                    continue;
                }
                if (m.cmd == CLSE) {
                    send(out, CLSE, localId, m.arg0, new byte[0]);
                    break;
                }
            }
            return new String(result.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (FinalError e) {
            throw e;
        } catch (IOException e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IOException("Stopped while " + stage + " (" + why + ")");
        }
    }

    private static void send(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte b : data) sum += (b & 0xff);
        ByteBuffer bb = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~cmd);
        bb.put(data);
        out.write(bb.array());
        out.flush();
    }

    private static Msg read(DataInputStream in) throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = bb.getInt();
        m.arg0 = bb.getInt();
        m.arg1 = bb.getInt();
        int len = bb.getInt();
        if (len < 0 || len > 1024 * 1024) throw new IOException("Bad ADB message.");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws IOException {
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key);
            c.update(SIG_PADDING);
            return c.doFinal(Arrays.copyOf(token, 20));
        } catch (Exception e) {
            throw new IOException("Couldn't sign ADB token: " + e.getMessage());
        }
    }

    /** Android's own public-key format: little-endian modulus words plus Montgomery values. */
    private static byte[] publicKeyBytes(RSAPublicKey pub) {
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger n = pub.getModulus();
        BigInteger r = BigInteger.ZERO.setBit(KEY_WORDS * 32);
        BigInteger rr = r.modPow(BigInteger.valueOf(2), n);
        BigInteger n0inv = n.remainder(r32).modInverse(r32);

        int[] myN = new int[KEY_WORDS];
        int[] myRr = new int[KEY_WORDS];
        for (int i = 0; i < KEY_WORDS; i++) {
            BigInteger[] res = rr.divideAndRemainder(r32);
            rr = res[0];
            myRr[i] = res[1].intValue();
            res = n.divideAndRemainder(r32);
            n = res[0];
            myN[i] = res[1].intValue();
        }
        ByteBuffer bb = ByteBuffer.allocate(4 + 4 + KEY_WORDS * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(KEY_WORDS);
        bb.putInt(n0inv.negate().intValue());
        for (int v : myN) bb.putInt(v);
        for (int v : myRr) bb.putInt(v);
        bb.putInt(pub.getPublicExponent().intValue());
        String encoded = Base64.encodeToString(bb.array(), Base64.NO_WRAP) + " myapps@firetv\0";
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    private static KeyPair keys(Context c) throws IOException {
        File priv = new File(c.getFilesDir(), "adbkey");
        File pub = new File(c.getFilesDir(), "adbkey.pub");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (priv.exists() && pub.exists()) {
                return new KeyPair(
                        kf.generatePublic(new X509EncodedKeySpec(readAll(pub))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            writeAll(priv, kp.getPrivate().getEncoded());
            writeAll(pub, kp.getPublic().getEncoded());
            return kp;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Couldn't prepare ADB key: " + e.getMessage());
        }
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        try (OutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
    }
}
