package ru.mcrpg.launcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class MinecraftServerListWriter {

    private static final String SERVERS_FILE_NAME = "servers.dat";
    private static final String SERVERS_TAG_NAME = "servers";
    private static final String SERVER_NAME_TAG = "name";
    private static final String SERVER_ADDRESS_TAG = "ip";
    private static final String SERVER_ICON_TAG = "icon";
    private static final String ACCEPT_TEXTURES_TAG = "acceptTextures";
    private static final String SERVER_ICON_RESOURCE = "/ru/mcrpg/launcher/assets/minecraft-server-icon.png";
    private static final String DEFAULT_SERVER_NAME = LauncherBrand.APP_TITLE;
    private static final String VANILLA_SERVER_NAME = "Minecraft Server";
    private static final String VANILLA_SERVER_NAME_RU = "\u0421\u0435\u0440\u0432\u0435\u0440 Minecraft";

    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_LONG = 4;
    private static final byte TAG_FLOAT = 5;
    private static final byte TAG_DOUBLE = 6;
    private static final byte TAG_BYTE_ARRAY = 7;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;
    private static final byte TAG_LONG_ARRAY = 12;

    public void upsert(LauncherConfig config) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("Конфиг лаунчера недоступен.");
        }

        Path gameDirectory = Paths.get(requireText(config.getGameDirectory(), "Папка игры не настроена."))
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(gameDirectory);

        Path serversFile = gameDirectory.resolve(SERVERS_FILE_NAME);
        CompoundTag root = Files.isRegularFile(serversFile) ? readRoot(serversFile) : new CompoundTag();
        ListTag servers = ensureServerList(root);
        String managedName = resolveManagedServerName();
        String address = resolveAddress(config);

        CompoundTag entryByName = findServerByName(servers, managedName);
        if (entryByName != null) {
            applyManagedServerEntry(entryByName, managedName, address);
            moveToTop(servers, entryByName);
            writeRoot(serversFile, root);
            return;
        }

        CompoundTag entryByAddress = findServerByAddress(servers, address);
        if (entryByAddress != null) {
            if (!hasText(entryByAddress.getString(SERVER_NAME_TAG)) || isDefaultMinecraftServerName(entryByAddress.getString(SERVER_NAME_TAG))) {
                entryByAddress.putString(SERVER_NAME_TAG, managedName);
            }
            applyManagedServerEntry(entryByAddress, entryByAddress.getString(SERVER_NAME_TAG), address);
            moveToTop(servers, entryByAddress);
            writeRoot(serversFile, root);
            return;
        }

        CompoundTag newEntry = new CompoundTag();
        applyManagedServerEntry(newEntry, managedName, address);
        servers.add(0, newEntry);
        writeRoot(serversFile, root);
    }

    private static String resolveManagedServerName() {
        return DEFAULT_SERVER_NAME;
    }

    private static String resolveAddress(LauncherConfig config) {
        String host = requireText(config.getServerHost(), "Хост сервера не настроен.");
        int port = config.getServerPort();
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Порт сервера вне допустимого диапазона: " + port);
        }
        return host + ":" + port;
    }

    private static void applyManagedServerEntry(CompoundTag entry, String name, String address) throws IOException {
        entry.putString(SERVER_NAME_TAG, requireText(name, "Имя сервера не настроено."));
        entry.putString(SERVER_ADDRESS_TAG, address);
        entry.put(ACCEPT_TEXTURES_TAG, new ByteTag((byte) 1));
        String icon = loadServerIcon();
        if (hasText(icon)) {
            entry.putString(SERVER_ICON_TAG, icon);
        }
    }

    private static void moveToTop(ListTag servers, CompoundTag entry) {
        List<Tag> values = servers.getValues();
        int index = values.indexOf(entry);
        if (index <= 0) {
            return;
        }
        values.remove(index);
        values.add(0, entry);
    }

    private static String loadServerIcon() throws IOException {
        try (InputStream inputStream = MinecraftServerListWriter.class.getResourceAsStream(SERVER_ICON_RESOURCE)) {
            if (inputStream == null) {
                return "";
            }
            byte[] bytes = readAllBytes(inputStream);
            if (bytes.length == 0) {
                return "";
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        DownloadUtils.copy(inputStream, output);
        return output.toByteArray();
    }

    private static ListTag ensureServerList(CompoundTag root) {
        ListTag servers = root.getList(SERVERS_TAG_NAME);
        if (servers == null || (!servers.getValues().isEmpty() && servers.getElementType() != TAG_COMPOUND)) {
            servers = new ListTag(TAG_COMPOUND, new ArrayList<Tag>());
            root.put(SERVERS_TAG_NAME, servers);
            return servers;
        }
        if (servers.getValues().isEmpty()) {
            servers.setElementType(TAG_COMPOUND);
        }
        return servers;
    }

    private static CompoundTag findServerByName(ListTag servers, String name) {
        for (Tag tag : servers.getValues()) {
            if (!(tag instanceof CompoundTag)) {
                continue;
            }
            CompoundTag server = (CompoundTag) tag;
            if (name.equals(server.getString(SERVER_NAME_TAG))) {
                return server;
            }
        }
        return null;
    }

    private static CompoundTag findServerByAddress(ListTag servers, String address) {
        for (Tag tag : servers.getValues()) {
            if (!(tag instanceof CompoundTag)) {
                continue;
            }
            CompoundTag server = (CompoundTag) tag;
            if (address.equals(server.getString(SERVER_ADDRESS_TAG))) {
                return server;
            }
        }
        return null;
    }

    private static boolean isDefaultMinecraftServerName(String value) {
        if (!hasText(value)) {
            return true;
        }
        String normalized = value.trim();
        return VANILLA_SERVER_NAME.equalsIgnoreCase(normalized) || VANILLA_SERVER_NAME_RU.equals(normalized);
    }

    private static CompoundTag readRoot(Path path) throws IOException {
        try (BufferedInputStream rawInput = new BufferedInputStream(Files.newInputStream(path))) {
            rawInput.mark(2);
            int first = rawInput.read();
            int second = rawInput.read();
            rawInput.reset();

            InputStream payloadInput = isGzipStream(first, second) ? new GZIPInputStream(rawInput) : rawInput;
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(payloadInput))) {
                byte type = readType(input);
                if (type != TAG_COMPOUND) {
                    throw new IOException("Корневой tag servers.dat не является compound.");
                }
                input.readUTF();
                return readCompound(input);
            }
        }
    }

    private static void writeRoot(Path path, CompoundTag root) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = Files.createTempFile(parent == null ? path.toAbsolutePath().getParent() : parent, "servers-", ".dat");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                output.writeByte(TAG_COMPOUND);
                output.writeUTF("");
                writeCompoundPayload(root, output);
            }
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static boolean isGzipStream(int first, int second) {
        return first == 0x1f && second == 0x8b;
    }

    private static Tag readPayload(byte type, DataInputStream input) throws IOException {
        switch (type) {
            case TAG_BYTE:
                return new ByteTag(input.readByte());
            case TAG_SHORT:
                return new ShortTag(input.readShort());
            case TAG_INT:
                return new IntTag(input.readInt());
            case TAG_LONG:
                return new LongTag(input.readLong());
            case TAG_FLOAT:
                return new FloatTag(input.readFloat());
            case TAG_DOUBLE:
                return new DoubleTag(input.readDouble());
            case TAG_BYTE_ARRAY:
                return new ByteArrayTag(readByteArray(input));
            case TAG_STRING:
                return new StringTag(input.readUTF());
            case TAG_LIST:
                return readList(input);
            case TAG_COMPOUND:
                return readCompound(input);
            case TAG_INT_ARRAY:
                return new IntArrayTag(readIntArray(input));
            case TAG_LONG_ARRAY:
                return new LongArrayTag(readLongArray(input));
            default:
                throw new IOException("Неподдерживаемый тип NBT tag: " + type);
        }
    }

    private static void writePayload(Tag tag, DataOutputStream output) throws IOException {
        switch (tag.typeId()) {
            case TAG_BYTE:
                output.writeByte(((ByteTag) tag).getValue());
                return;
            case TAG_SHORT:
                output.writeShort(((ShortTag) tag).getValue());
                return;
            case TAG_INT:
                output.writeInt(((IntTag) tag).getValue());
                return;
            case TAG_LONG:
                output.writeLong(((LongTag) tag).getValue());
                return;
            case TAG_FLOAT:
                output.writeFloat(((FloatTag) tag).getValue());
                return;
            case TAG_DOUBLE:
                output.writeDouble(((DoubleTag) tag).getValue());
                return;
            case TAG_BYTE_ARRAY:
                writeByteArray(((ByteArrayTag) tag).getValue(), output);
                return;
            case TAG_STRING:
                output.writeUTF(((StringTag) tag).getValue());
                return;
            case TAG_LIST:
                writeListPayload((ListTag) tag, output);
                return;
            case TAG_COMPOUND:
                writeCompoundPayload((CompoundTag) tag, output);
                return;
            case TAG_INT_ARRAY:
                writeIntArray(((IntArrayTag) tag).getValue(), output);
                return;
            case TAG_LONG_ARRAY:
                writeLongArray(((LongArrayTag) tag).getValue(), output);
                return;
            default:
                throw new IOException("Неподдерживаемый тип NBT tag: " + tag.typeId());
        }
    }

    private static CompoundTag readCompound(DataInputStream input) throws IOException {
        CompoundTag compound = new CompoundTag();
        while (true) {
            byte type = readType(input);
            if (type == TAG_END) {
                return compound;
            }
            String name = input.readUTF();
            compound.put(name, readPayload(type, input));
        }
    }

    private static void writeCompoundPayload(CompoundTag compound, DataOutputStream output) throws IOException {
        for (Map.Entry<String, Tag> entry : compound.getEntries().entrySet()) {
            output.writeByte(entry.getValue().typeId());
            output.writeUTF(entry.getKey());
            writePayload(entry.getValue(), output);
        }
        output.writeByte(TAG_END);
    }

    private static ListTag readList(DataInputStream input) throws IOException {
        byte elementType = readType(input);
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Отрицательная длина NBT list.");
        }

        List<Tag> values = new ArrayList<Tag>(length);
        for (int index = 0; index < length; index++) {
            values.add(readPayload(elementType, input));
        }
        return new ListTag(elementType, values);
    }

    private static void writeListPayload(ListTag list, DataOutputStream output) throws IOException {
        output.writeByte(list.getElementType());
        output.writeInt(list.getValues().size());
        for (Tag tag : list.getValues()) {
            writePayload(tag, output);
        }
    }

    private static byte[] readByteArray(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Отрицательная длина NBT byte array.");
        }
        byte[] value = new byte[length];
        input.readFully(value);
        return value;
    }

    private static void writeByteArray(byte[] value, DataOutputStream output) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static int[] readIntArray(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Отрицательная длина NBT int array.");
        }
        int[] value = new int[length];
        for (int index = 0; index < length; index++) {
            value[index] = input.readInt();
        }
        return value;
    }

    private static void writeIntArray(int[] value, DataOutputStream output) throws IOException {
        output.writeInt(value.length);
        for (int item : value) {
            output.writeInt(item);
        }
    }

    private static long[] readLongArray(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Отрицательная длина NBT long array.");
        }
        long[] value = new long[length];
        for (int index = 0; index < length; index++) {
            value[index] = input.readLong();
        }
        return value;
    }

    private static void writeLongArray(long[] value, DataOutputStream output) throws IOException {
        output.writeInt(value.length);
        for (long item : value) {
            output.writeLong(item);
        }
    }

    private static byte readType(DataInputStream input) throws IOException {
        return (byte) input.readUnsignedByte();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private abstract static class Tag {
        abstract byte typeId();
    }

    private static final class ByteTag extends Tag {
        private final byte value;

        private ByteTag(byte value) {
            this.value = value;
        }

        @Override
        byte typeId() {
            return TAG_BYTE;
        }

        private byte getValue() {
            return value;
        }
    }

    private static final class ShortTag extends Tag {
        private final short value;

        private ShortTag(short value) {
            this.value = value;
        }

        @Override
        byte typeId() {
            return TAG_SHORT;
        }

        private short getValue() {
            return value;
        }
    }

    private static final class IntTag extends Tag {
        private final int value;

        private IntTag(int value) {
            this.value = value;
        }

        @Override
        byte typeId() {
            return TAG_INT;
        }

        private int getValue() {
            return value;
        }
    }

    private static final class LongTag extends Tag {
        private final long value;

        private LongTag(long value) {
            this.value = value;
        }

        @Override
        byte typeId() {
            return TAG_LONG;
        }

        private long getValue() {
            return value;
        }
    }

    private static final class FloatTag extends Tag {
        private final float value;

        private FloatTag(float value) {
            this.value = value;
        }

        @Override
        byte typeId() {
            return TAG_FLOAT;
        }

        private float getValue() {
            return value;
        }
    }

    private static final class DoubleTag extends Tag {
        private final double value;

        private DoubleTag(double value) {
            this.value = value;
        }

        @Override
        byte typeId() {
            return TAG_DOUBLE;
        }

        private double getValue() {
            return value;
        }
    }

    private static final class ByteArrayTag extends Tag {
        private final byte[] value;

        private ByteArrayTag(byte[] value) {
            this.value = value == null ? new byte[0] : value.clone();
        }

        @Override
        byte typeId() {
            return TAG_BYTE_ARRAY;
        }

        private byte[] getValue() {
            return value.clone();
        }
    }

    private static final class StringTag extends Tag {
        private final String value;

        private StringTag(String value) {
            this.value = value == null ? "" : value;
        }

        @Override
        byte typeId() {
            return TAG_STRING;
        }

        private String getValue() {
            return value;
        }
    }

    private static final class ListTag extends Tag {
        private byte elementType;
        private final List<Tag> values;

        private ListTag(byte elementType, List<Tag> values) {
            this.elementType = elementType;
            this.values = values == null ? new ArrayList<Tag>() : values;
        }

        @Override
        byte typeId() {
            return TAG_LIST;
        }

        private byte getElementType() {
            return elementType;
        }

        private void setElementType(byte elementType) {
            this.elementType = elementType;
        }

        private List<Tag> getValues() {
            return values;
        }

        private void add(int index, Tag value) {
            if (value == null) {
                throw new IllegalArgumentException("Значение NBT list обязательно.");
            }
            if (values.isEmpty() && elementType == TAG_END) {
                elementType = value.typeId();
            }
            if (elementType != value.typeId()) {
                throw new IllegalArgumentException("Тип элемента NBT list не совпадает.");
            }
            values.add(index, value);
        }
    }

    private static final class CompoundTag extends Tag {
        private final LinkedHashMap<String, Tag> entries = new LinkedHashMap<String, Tag>();

        @Override
        byte typeId() {
            return TAG_COMPOUND;
        }

        private Map<String, Tag> getEntries() {
            return entries;
        }

        private Tag get(String name) {
            return entries.get(name);
        }

        private ListTag getList(String name) {
            Tag tag = get(name);
            return tag instanceof ListTag ? (ListTag) tag : null;
        }

        private String getString(String name) {
            Tag tag = get(name);
            if (!(tag instanceof StringTag)) {
                return "";
            }
            return ((StringTag) tag).getValue();
        }

        private void put(String name, Tag value) {
            if (name == null || value == null) {
                throw new IllegalArgumentException("Запись NBT compound неполная.");
            }
            entries.put(name, value);
        }

        private void putString(String name, String value) {
            put(name, new StringTag(value));
        }
    }

    private static final class IntArrayTag extends Tag {
        private final int[] value;

        private IntArrayTag(int[] value) {
            this.value = value == null ? new int[0] : value.clone();
        }

        @Override
        byte typeId() {
            return TAG_INT_ARRAY;
        }

        private int[] getValue() {
            return value.clone();
        }
    }

    private static final class LongArrayTag extends Tag {
        private final long[] value;

        private LongArrayTag(long[] value) {
            this.value = value == null ? new long[0] : value.clone();
        }

        @Override
        byte typeId() {
            return TAG_LONG_ARRAY;
        }

        private long[] getValue() {
            return value.clone();
        }
    }
}
