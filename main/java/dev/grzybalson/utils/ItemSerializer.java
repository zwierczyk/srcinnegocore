package dev.grzybalson.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class ItemSerializer {

    private static final String SINGLE_ITEM_PREFIX = "bytes:";
    private static final String ITEM_ARRAY_PREFIX = "bytes-array:";
    private static final String ITEM_ARRAY_V2_PREFIX = "bytes-array-v2:";
    private static final String ITEM_ARRAY_V3_PREFIX = "bytes-array-v3:";
    private static final byte ITEM_CODEC_NATIVE_BYTES = 1;
    private static final byte ITEM_CODEC_SERIALIZED_STRING = 2;
    private static final int MAX_ITEM_BYTES = 16_777_216;

    public static String serializeItem(ItemStack item) {
        if (item == null) {
            return null;
        }

        try {
            return SINGLE_ITEM_PREFIX + Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Throwable ignored) {
            // Fallback for older servers/APIs.
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (IOException e) {
            return null;
        }
    }

    private static ItemStack finishDeserialize(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemClientSanitizer.sanitizeInPlace(item);
        return item;
    }

    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        String normalized = data.trim();
        if (normalized.startsWith(SINGLE_ITEM_PREFIX)) {
            try {
                byte[] bytes = decodeBase64(normalized.substring(SINGLE_ITEM_PREFIX.length()));
                return finishDeserialize(deserializeItemBytesVariants(bytes));
            } catch (Throwable ignored) {
                return null;
            }
        }

        try {
            return finishDeserialize(deserializeItemBytesVariants(decodeBase64(normalized)));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static byte[] decodeBase64(String data) {
        return Base64.getMimeDecoder().decode(data);
    }

    private static ItemStack deserializeItemBytes(byte[] bytes) {
        ItemStack item = deserializeNativeBytes(bytes);
        return item != null ? item : deserializeObjectBytes(bytes);
    }

    private static ItemStack deserializeItemBytesVariants(byte[] bytes) {
        ItemStack item = deserializeItemBytes(bytes);
        if (item != null) {
            return item;
        }
        if (isGzip(bytes)) {
            try {
                item = deserializeItemBytes(gunzip(bytes));
                if (item != null) {
                    return item;
                }
            } catch (IOException ignored) {
            }
        }
        if (isZlib(bytes)) {
            try {
                item = deserializeItemBytes(inflate(bytes));
                if (item != null) {
                    return item;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static ItemStack deserializeNativeBytes(byte[] bytes) {
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack deserializeObjectBytes(byte[] bytes) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            Object object = dataInput.readObject();
            return object instanceof ItemStack itemStack ? itemStack : null;

        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isGzip(byte[] bytes) {
        return bytes != null
            && bytes.length >= 2
            && (bytes[0] & 0xFF) == 0x1F
            && (bytes[1] & 0xFF) == 0x8B;
    }

    private static boolean isZlib(byte[] bytes) {
        return bytes != null
            && bytes.length >= 2
            && (bytes[0] & 0xFF) == 0x78;
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {

            return readCompressed(gzipInputStream);
        }
    }

    private static byte[] inflate(byte[] bytes) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             InflaterInputStream inflaterInputStream = new InflaterInputStream(inputStream)) {

            return readCompressed(inflaterInputStream);
        }
    }

    private static byte[] readCompressed(java.io.InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ITEM_BYTES) {
                    throw new IOException("Serialized item is too large");
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    public static String serializeItems(ItemStack[] items) {
        if (items == null) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutput = new DataOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null) {
                    dataOutput.writeBoolean(false);
                    continue;
                }

                dataOutput.writeBoolean(true);
                try {
                    byte[] bytes = item.serializeAsBytes();
                    dataOutput.writeByte(ITEM_CODEC_NATIVE_BYTES);
                    dataOutput.writeInt(bytes.length);
                    dataOutput.write(bytes);
                } catch (Throwable ignored) {
                    String serialized = serializeItem(item);
                    if (serialized == null) {
                        dataOutput.writeByte(ITEM_CODEC_SERIALIZED_STRING);
                        dataOutput.writeInt(0);
                        continue;
                    }
                    byte[] bytes = serialized.getBytes(StandardCharsets.UTF_8);
                    dataOutput.writeByte(ITEM_CODEC_SERIALIZED_STRING);
                    dataOutput.writeInt(bytes.length);
                    dataOutput.write(bytes);
                }
            }
            return ITEM_ARRAY_V3_PREFIX + Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (IOException e) {
            return null;
        }
    }

    public static ItemStack[] deserializeItems(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        if (data.startsWith(ITEM_ARRAY_V3_PREFIX)) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data.substring(ITEM_ARRAY_V3_PREFIX.length())));
                 DataInputStream dataInput = new DataInputStream(inputStream)) {

                int length = dataInput.readInt();
                if (length < 0 || length > 10_000) {
                    return null;
                }
                ItemStack[] items = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    if (!dataInput.readBoolean()) {
                        continue;
                    }

                    byte codec = dataInput.readByte();
                    int byteLength = dataInput.readInt();
                    if (byteLength < 0 || byteLength > MAX_ITEM_BYTES) {
                        break;
                    }
                    byte[] bytes = new byte[byteLength];
                    dataInput.readFully(bytes);

                    if (byteLength == 0) {
                        continue;
                    }
                    if (codec == ITEM_CODEC_NATIVE_BYTES) {
                        try {
                            items[i] = finishDeserialize(ItemStack.deserializeBytes(bytes));
                        } catch (Throwable ignored) {
                            items[i] = null;
                        }
                    } else if (codec == ITEM_CODEC_SERIALIZED_STRING) {
                        items[i] = deserializeItem(new String(bytes, StandardCharsets.UTF_8));
                    }
                }
                return items;
            } catch (IOException | IllegalArgumentException e) {
                return null;
            }
        }

        if (data.startsWith(ITEM_ARRAY_V2_PREFIX)) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data.substring(ITEM_ARRAY_V2_PREFIX.length())));
                 DataInputStream dataInput = new DataInputStream(inputStream)) {

                int length = dataInput.readInt();
                if (length < 0 || length > 10_000) {
                    return null;
                }
                ItemStack[] items = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    boolean hasItem = dataInput.readBoolean();
                    if (hasItem) {
                        int byteLength = dataInput.readInt();
                        if (byteLength < 0 || byteLength > MAX_ITEM_BYTES) {
                            break;
                        }
                        byte[] bytes = new byte[byteLength];
                        dataInput.readFully(bytes);
                        items[i] = deserializeItem(new String(bytes, StandardCharsets.UTF_8));
                    } else {
                        items[i] = null;
                    }
                }
                return items;
            } catch (IOException | IllegalArgumentException e) {
                return null;
            }
        }

        if (data.startsWith(ITEM_ARRAY_PREFIX)) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data.substring(ITEM_ARRAY_PREFIX.length())));
                 DataInputStream dataInput = new DataInputStream(inputStream)) {

                int length = dataInput.readInt();
                if (length < 0 || length > 10_000) {
                    return null;
                }
                ItemStack[] items = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    boolean hasItem = dataInput.readBoolean();
                    items[i] = hasItem ? deserializeItem(dataInput.readUTF()) : null;
                }
                return items;
            } catch (IOException | IllegalArgumentException e) {
                return null;
            }
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            int length = dataInput.readInt();
            if (length < 0 || length > 10_000) {
                return null;
            }
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                Object object = dataInput.readObject();
                items[i] = object instanceof ItemStack itemStack ? finishDeserialize(itemStack) : null;
            }
            return items;

        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            return null;
        }
    }
}
