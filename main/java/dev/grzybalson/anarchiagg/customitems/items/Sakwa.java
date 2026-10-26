package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Sakwa implements Listener {

    private final String sakwaName;
    private final JavaPlugin plugin;
    private final Material sakwaMaterial;
    private final SakwaDatabaseHandler dbHandler;
    private final NamespacedKey sakwaIdKey;
    private final String guiTitle;
    private final int sakwaCapacity = 45;
    private final int cooldownSeconds;
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private final Map<UUID, Long> clickCooldown = new HashMap<>();
    private final Map<UUID, String> openSakwaIds = new HashMap<>();

    public Sakwa(JavaPlugin plugin) {
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        this.plugin = plugin;
        this.sakwaIdKey = new NamespacedKey(plugin, "sakwa_id");

        String sakwaItem = eventowkiConfig.getString("eventowki.sakwa.item", "CHEST_MINECART");
        sakwaName = ChatColor.translateAlternateColorCodes('&', eventowkiConfig.getString("eventowki.sakwa.name", "&6&lSakwa"));
        sakwaMaterial = Material.valueOf(sakwaItem.toUpperCase());
        cooldownSeconds = eventowkiConfig.getInt("eventowki.sakwa.cooldown", 0);
        guiTitle = ChatColor.translateAlternateColorCodes('§', "§8Sakiewka Dropu");

        this.dbHandler = new SakwaDatabaseHandler(plugin);
        this.dbHandler.initialize();
    }

    private String getFirstSakwaId(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (EventItemRegistry.matches(plugin, "sakwa", item)) {
                return getOrCreateSakwaId(item);
            }
        }
        return null;
    }

    private String getOrCreateSakwaId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !EventItemRegistry.matches(plugin, "sakwa", item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String existing = meta.getPersistentDataContainer().get(sakwaIdKey, PersistentDataType.STRING);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }

        String created = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(sakwaIdKey, PersistentDataType.STRING, created);
        item.setItemMeta(meta);
        return created;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastDamager.remove(uuid);
        lastDamageTime.remove(uuid);
        clickCooldown.remove(uuid);
        openSakwaIds.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged)) return;

        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }

        if (damager != null && !damager.equals(damaged)) {
            lastDamager.put(damaged.getUniqueId(), damager.getUniqueId());
            lastDamageTime.put(damaged.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity();
        ItemStack main = killed.getInventory().getItemInMainHand();
        ItemStack off = killed.getInventory().getItemInOffHand();

        if (isTotem(main) || isTotem(off)) return;

        UUID killedUUID = killed.getUniqueId();
        Player killer = getKiller(killed);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Entity entity : killed.getNearbyEntities(5, 5, 5)) {
                if (entity instanceof Item itemEntity) {
                    itemEntity.setVelocity(itemEntity.getVelocity().multiply(0.35));
                }
            }
        }, 1L);

        String sakwaId = killer == null || killer.equals(killed) ? null : getFirstSakwaId(killer);
        if (sakwaId == null) {
            lastDamager.remove(killedUUID);
            lastDamageTime.remove(killedUUID);
            return;
        }

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        UUID killerUUID = killer.getUniqueId();
        Location dropLoc = killed.getLocation();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int currentCount = dbHandler.getItemCount(sakwaId);
            int space = sakwaCapacity - currentCount;

            List<ItemStack> toSakwa = new ArrayList<>();
            List<ItemStack> toGround = new ArrayList<>();

            for (ItemStack item : drops) {
                if (item == null || item.getType() == Material.AIR) continue;
                if (space > 0) {
                    toSakwa.add(item);
                    space--;
                } else {
                    toGround.add(item);
                }
            }

            if (!toSakwa.isEmpty()) dbHandler.addItemsBatch(killerUUID, sakwaId, toSakwa);
            if (!toGround.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (ItemStack item : toGround) {
                        if (dropLoc.getWorld() != null) {
                            dropLoc.getWorld().dropItemNaturally(dropLoc, item);
                        }
                    }
                });
            }
        });

        lastDamager.remove(killedUUID);
        lastDamageTime.remove(killedUUID);
    }

    private boolean isTotem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return EventItemRegistry.matches(plugin, "totemulaskawienia", item);
    }

    private Player getKiller(Player killed) {
        UUID id = killed.getUniqueId();
        long KILL_ASSIST_TIME = 90000L;
        if (lastDamager.containsKey(id) && System.currentTimeMillis() - lastDamageTime.get(id) <= KILL_ASSIST_TIME) {
            Player p = Bukkit.getPlayer(lastDamager.get(id));
            if (p != null) return p;
        }
        return killed.getKiller();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (EventItemRegistry.matches(plugin, "sakwa", item)) {
            event.setCancelled(true);
            String sakwaId = getOrCreateSakwaId(item);
            if (sakwaId == null) return;
            dbHandler.attachLegacyItems(player.getUniqueId(), sakwaId);
            if (EventItemUseGuard.hasItemCooldown(plugin, player, "sakwa", item, cooldownSeconds)) {
                return;
            }
            EventItemUseGuard.applyItemCooldown(plugin, player, "sakwa", item, cooldownSeconds);
            openSakwaGUI(player, sakwaId);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }

    private void openSakwaGUI(Player player, String sakwaId) {
        Inventory gui = Bukkit.createInventory(null, 54, guiTitle);
        openSakwaIds.put(player.getUniqueId(), sakwaId);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<SakwaItem> items = dbHandler.getItems(sakwaId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < items.size() && i < sakwaCapacity; i++) {
                    gui.setItem(i, items.get(i).item());
                }
                gui.setItem(49, createGuiItem(Material.BARRIER, "&c&lᴢᴀᴍᴋɴɪᴊ", Arrays.asList("", "&8» &7Kliknij aby zamknąć!", "")));
                gui.setItem(50, createGuiItem(Material.LIME_DYE, "&a&lᴡʏᴘʟᴀᴄ ꜱᴀᴋᴡᴇ", Arrays.asList("&7", "&8» &7Kliknij, aby wypłacić przedmioty z sakiekwki!", "")));
                player.openInventory(gui);
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null || !event.getView().getTitle().equals(guiTitle)) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        if (clickCooldown.containsKey(uuid) && (now - clickCooldown.get(uuid)) < 500L) {
            return;
        }
        clickCooldown.put(uuid, now);

        int slot = event.getSlot();

        if (slot == 49) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 50) {
            handleWithdrawAll(player);
        } else if (slot >= 0 && slot < sakwaCapacity) {
            handleSingleWithdraw(player, slot);
        }
    }

    private void handleWithdrawAll(Player player) {
        player.closeInventory();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sakwaId = getOpenSakwaId(player);
            if (sakwaId == null) return;
            List<SakwaItem> items = dbHandler.getItems(sakwaId);
            if (items.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", ChatColor.translateAlternateColorCodes('&', "&cTwoja Sakiewka Dropu jest pusta!"), 0, 50, 10);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                });
                return;
            }

            dbHandler.clearItems(sakwaId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (SakwaItem si : items) {
                    Map<Integer, ItemStack> left = player.getInventory().addItem(si.item());
                    if (!left.isEmpty()) {
                        left.values().forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
                    }
                }
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", ChatColor.translateAlternateColorCodes('&', "&aWypłacanie przedmiotów..."), 0, 50, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            });
        });
    }

    private void handleSingleWithdraw(Player player, int slot) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sakwaId = getOpenSakwaId(player);
            if (sakwaId == null) return;
            List<SakwaItem> items = dbHandler.getItems(sakwaId);
            if (slot >= items.size()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getInventory().firstEmpty() == -1) {
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", ChatColor.translateAlternateColorCodes('&', "&cBrak miejsca w ekwipunku!"), 0, 50, 10);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.closeInventory();
                    return;
                }

                SakwaItem clicked = items.get(slot);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    dbHandler.removeItem(clicked.dbId());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.getInventory().addItem(clicked.item());
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        openSakwaGUI(player, sakwaId);
                    });
                });
            });
        });
    }

    private String getOpenSakwaId(Player player) {
        String openId = openSakwaIds.get(player.getUniqueId());
        if (openId != null && !openId.isBlank()) {
            return openId;
        }
        ItemStack cursorItem = player.getInventory().getItemInMainHand();
        String id = getOrCreateSakwaId(cursorItem);
        return id != null ? id : getFirstSakwaId(player);
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private record SakwaItem(int dbId, ItemStack item) {}

    public static class SakwaDatabaseHandler {
        private final JavaPlugin plugin;
        private Connection connection;

        public SakwaDatabaseHandler(JavaPlugin plugin) { this.plugin = plugin; }

        public synchronized void addItemsBatch(UUID uuid, String sakwaId, List<ItemStack> items) {
            try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO sakwa_items(player_uuid, sakwa_id, item_base64) VALUES(?,?,?)")) {
                connection.setAutoCommit(false);
                for (ItemStack item : items) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, sakwaId);
                    pstmt.setString(3, serialize(item));
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException e) { e.printStackTrace(); }
        }

        public synchronized List<SakwaItem> getItems(String sakwaId) {
            List<SakwaItem> list = new ArrayList<>();
            try (PreparedStatement pstmt = connection.prepareStatement("SELECT id, item_base64 FROM sakwa_items WHERE sakwa_id = ?")) {
                pstmt.setString(1, sakwaId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    list.add(new SakwaItem(rs.getInt("id"), deserialize(rs.getString("item_base64"))));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        }

        public synchronized int getItemCount(String sakwaId) {
            try (PreparedStatement pstmt = connection.prepareStatement("SELECT COUNT(*) FROM sakwa_items WHERE sakwa_id = ?")) {
                pstmt.setString(1, sakwaId);
                ResultSet rs = pstmt.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) { return 0; }
        }

        public synchronized void removeItem(int id) {
            try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM sakwa_items WHERE id = ?")) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }

        public synchronized void clearItems(String sakwaId) {
            try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM sakwa_items WHERE sakwa_id = ?")) {
                pstmt.setString(1, sakwaId);
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }

        public synchronized void attachLegacyItems(UUID uuid, String sakwaId) {
            try (PreparedStatement pstmt = connection.prepareStatement("UPDATE sakwa_items SET sakwa_id = ? WHERE player_uuid = ? AND (sakwa_id IS NULL OR sakwa_id = '')")) {
                pstmt.setString(1, sakwaId);
                pstmt.setString(2, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }

        private String serialize(ItemStack item) {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream(); BukkitObjectOutputStream oos = new BukkitObjectOutputStream(os)) {
                oos.writeObject(item);
                return Base64Coder.encodeLines(os.toByteArray());
            } catch (Exception e) { return ""; }
        }

        private ItemStack deserialize(String base64) {
            try (ByteArrayInputStream is = new ByteArrayInputStream(Base64Coder.decodeLines(base64)); BukkitObjectInputStream ois = new BukkitObjectInputStream(is)) {
                return (ItemStack) ois.readObject();
            } catch (Exception e) { return new ItemStack(Material.AIR); }
        }
        public void initialize() {
            try {
                File dbFile = dev.grzybalson.anarchiagg.AnarchiaGGPaths.getFile(plugin, "eventowki/sakwa.db");
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA synchronous=NORMAL;");
                    stmt.execute("CREATE TABLE IF NOT EXISTS sakwa_items (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT, item_base64 TEXT)");
                    ensureColumn(stmt, "sakwa_id", "TEXT");
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        private void ensureColumn(Statement stmt, String column, String type) throws SQLException {
            try {
                stmt.execute("ALTER TABLE sakwa_items ADD COLUMN " + column + " " + type);
            } catch (SQLException ignored) {
            }
        }
    }
}
