package ru.mcrpg.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinecraftServerListWriterTest {

    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;

    @TempDir
    Path tempDirectory;

    private final MinecraftServerListWriter writer = new MinecraftServerListWriter();

    @Test
    void upsertCreatesSavedServerEntryWhenFileIsMissing() throws IOException {
        Path gameDirectory = tempDirectory.resolve("client");
        LauncherConfig config = LauncherConfig.defaults();
        config.setGameDirectory(gameDirectory.toString());
        config.setServerHost("play.example.com");
        config.setServerPort(25570);

        writer.upsert(config);

        List<Map<String, Object>> servers = readServersDat(gameDirectory.resolve("servers.dat"));
        assertEquals(1, servers.size());
        assertManagedServer(servers.get(0), LauncherBrand.APP_TITLE, "play.example.com:25570");
        assertTrue(Files.isRegularFile(gameDirectory.resolve("servers.dat")));
    }

    @Test
    void upsertUpdatesManagedEntryAndPreservesOtherServerData() throws IOException {
        Path gameDirectory = tempDirectory.resolve("client");
        Path serversFile = gameDirectory.resolve("servers.dat");
        writeServersDat(
            serversFile,
            new ServerFixture(LauncherBrand.APP_TITLE, "old.example.com:25565", Byte.valueOf((byte) 1)),
            new ServerFixture("Other Realm", "other.example.com:25565", null)
        );

        LauncherConfig config = LauncherConfig.defaults();
        config.setGameDirectory(gameDirectory.toString());
        config.setServerHost("play.example.com");
        config.setServerPort(25580);

        writer.upsert(config);

        List<Map<String, Object>> rawServers = readServersDat(serversFile);
        assertEquals(2, rawServers.size());
        assertManagedServer(rawServers.get(0), LauncherBrand.APP_TITLE, "play.example.com:25580");
        assertServer(rawServers.get(1), "Other Realm", "other.example.com:25565");
        assertEquals(Byte.valueOf((byte) 1), rawServers.get(0).get("acceptTextures"));
    }

    @Test
    void upsertKeepsExistingCustomNameWhenRouteAlreadyExists() throws IOException {
        Path gameDirectory = tempDirectory.resolve("client");
        Path serversFile = gameDirectory.resolve("servers.dat");
        writeServersDat(serversFile, new ServerFixture("My RPG Server", "play.example.com:25565", null));

        LauncherConfig config = LauncherConfig.defaults();
        config.setGameDirectory(gameDirectory.toString());
        config.setServerHost("play.example.com");
        config.setServerPort(25565);

        writer.upsert(config);

        List<Map<String, Object>> servers = readServersDat(serversFile);
        assertEquals(1, servers.size());
        assertManagedServer(servers.get(0), "My RPG Server", "play.example.com:25565");
    }

    @Test
    void upsertReadsLegacyGzipServerList() throws IOException {
        Path gameDirectory = tempDirectory.resolve("client");
        Path serversFile = gameDirectory.resolve("servers.dat");
        writeServersDatGzip(serversFile, new ServerFixture("Old Entry", "old.example.com:25565", null));

        LauncherConfig config = LauncherConfig.defaults();
        config.setGameDirectory(gameDirectory.toString());
        config.setServerHost("play.example.com");
        config.setServerPort(25565);

        writer.upsert(config);

        List<Map<String, Object>> servers = readServersDat(serversFile);
        assertEquals(2, servers.size());
        assertManagedServer(servers.get(0), LauncherBrand.APP_TITLE, "play.example.com:25565");
        assertServer(servers.get(1), "Old Entry", "old.example.com:25565");
    }

    @Test
    void upsertMovesExistingRouteToTop() throws IOException {
        Path gameDirectory = tempDirectory.resolve("client");
        Path serversFile = gameDirectory.resolve("servers.dat");
        writeServersDat(
            serversFile,
            new ServerFixture("Other Realm", "other.example.com:25565", null),
            new ServerFixture("My RPG Server", "play.example.com:25565", null)
        );

        LauncherConfig config = LauncherConfig.defaults();
        config.setGameDirectory(gameDirectory.toString());
        config.setServerHost("play.example.com");
        config.setServerPort(25565);

        writer.upsert(config);

        List<Map<String, Object>> servers = readServersDat(serversFile);
        assertEquals(2, servers.size());
        assertManagedServer(servers.get(0), "My RPG Server", "play.example.com:25565");
        assertServer(servers.get(1), "Other Realm", "other.example.com:25565");
    }

    private static void writeServersDat(Path path, ServerFixture... servers) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            writeServersDatPayload(output, servers);
        }
    }

    private static void writeServersDatGzip(Path path, ServerFixture... servers) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(path))))) {
            writeServersDatPayload(output, servers);
        }
    }

    private static void writeServersDatPayload(DataOutputStream output, ServerFixture... servers) throws IOException {
        output.writeByte(TAG_COMPOUND);
        output.writeUTF("");

        output.writeByte(TAG_LIST);
        output.writeUTF("servers");
        output.writeByte(TAG_COMPOUND);
        output.writeInt(servers.length);

        for (ServerFixture server : servers) {
            writeStringTag(output, "name", server.name);
            writeStringTag(output, "ip", server.address);
            if (server.acceptTextures != null) {
                output.writeByte(TAG_BYTE);
                output.writeUTF("acceptTextures");
                output.writeByte(server.acceptTextures.byteValue());
            }
            output.writeByte(TAG_END);
        }

        output.writeByte(TAG_END);
    }

    private static List<Map<String, Object>> readServersDat(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            assertEquals(TAG_COMPOUND, input.readUnsignedByte());
            input.readUTF();

            while (true) {
                int type = input.readUnsignedByte();
                if (type == TAG_END) {
                    return new ArrayList<Map<String, Object>>();
                }

                String name = input.readUTF();
                if (type == TAG_LIST && "servers".equals(name)) {
                    return readServerList(input);
                }
                skipPayload(type, input);
            }
        }
    }

    private static List<Map<String, Object>> readServerList(DataInputStream input) throws IOException {
        int elementType = input.readUnsignedByte();
        int length = input.readInt();
        List<Map<String, Object>> servers = new ArrayList<Map<String, Object>>(length);
        for (int index = 0; index < length; index++) {
            if (elementType != TAG_COMPOUND) {
                skipPayload(elementType, input);
                continue;
            }
            servers.add(readCompound(input));
        }
        return servers;
    }

    private static Map<String, Object> readCompound(DataInputStream input) throws IOException {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        while (true) {
            int type = input.readUnsignedByte();
            if (type == TAG_END) {
                return values;
            }
            String name = input.readUTF();
            values.put(name, readPayload(type, input));
        }
    }

    private static Object readPayload(int type, DataInputStream input) throws IOException {
        switch (type) {
            case TAG_BYTE:
                return Byte.valueOf(input.readByte());
            case TAG_STRING:
                return input.readUTF();
            case TAG_COMPOUND:
                return readCompound(input);
            case TAG_LIST:
                return readServerList(input);
            default:
                throw new IOException("Unsupported test NBT tag type: " + type);
        }
    }

    private static void skipPayload(int type, DataInputStream input) throws IOException {
        readPayload(type, input);
    }

    private static void writeStringTag(DataOutputStream output, String name, String value) throws IOException {
        output.writeByte(TAG_STRING);
        output.writeUTF(name);
        output.writeUTF(value);
    }

    private static void assertServer(Map<String, Object> server, String name, String address) {
        assertEquals(name, server.get("name"));
        assertEquals(address, server.get("ip"));
    }

    private static void assertManagedServer(Map<String, Object> server, String name, String address) {
        assertServer(server, name, address);
        assertEquals(Byte.valueOf((byte) 1), server.get("acceptTextures"));
        Object icon = server.get("icon");
        assertTrue(icon instanceof String);
        assertTrue(((String) icon).startsWith("data:image/png;base64,"));
    }

    private static final class ServerFixture {
        private final String name;
        private final String address;
        private final Byte acceptTextures;

        private ServerFixture(String name, String address, Byte acceptTextures) {
            this.name = name;
            this.address = address;
            this.acceptTextures = acceptTextures;
        }
    }
}
