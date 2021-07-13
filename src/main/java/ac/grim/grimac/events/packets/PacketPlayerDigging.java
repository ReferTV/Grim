package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAC;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.AlmostBoolean;
import ac.grim.grimac.utils.data.packetentity.latency.BlockPlayerUpdate;
import ac.grim.grimac.utils.nmsImplementations.Materials;
import ac.grim.grimac.utils.nmsImplementations.XMaterial;
import io.github.retrooper.packetevents.event.PacketListenerAbstract;
import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.blockdig.WrappedPacketInBlockDig;
import io.github.retrooper.packetevents.packetwrappers.play.in.blockplace.WrappedPacketInBlockPlace;
import io.github.retrooper.packetevents.packetwrappers.play.in.helditemslot.WrappedPacketInHeldItemSlot;
import io.github.retrooper.packetevents.packetwrappers.play.in.useitem.WrappedPacketInUseItem;
import io.github.retrooper.packetevents.utils.player.ClientVersion;
import io.github.retrooper.packetevents.utils.player.Hand;
import io.github.retrooper.packetevents.utils.server.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;

public class PacketPlayerDigging extends PacketListenerAbstract {

    private static final Material CROSSBOW = XMaterial.CROSSBOW.parseMaterial();
    private static final Material BOW = XMaterial.BOW.parseMaterial();
    private static final Material TRIDENT = XMaterial.TRIDENT.parseMaterial();
    private static final Material SHIELD = XMaterial.SHIELD.parseMaterial();

    private static final Material ARROW = XMaterial.ARROW.parseMaterial();
    private static final Material TIPPED_ARROW = XMaterial.TIPPED_ARROW.parseMaterial();
    private static final Material SPECTRAL_ARROW = XMaterial.SPECTRAL_ARROW.parseMaterial();

    private static final Material POTION = XMaterial.POTION.parseMaterial();
    private static final Material MILK_BUCKET = XMaterial.MILK_BUCKET.parseMaterial();

    private static final Material APPLE = XMaterial.APPLE.parseMaterial();
    private static final Material GOLDEN_APPLE = XMaterial.GOLDEN_APPLE.parseMaterial();
    private static final Material ENCHANTED_GOLDEN_APPLE = XMaterial.ENCHANTED_GOLDEN_APPLE.parseMaterial();
    private static final Material HONEY_BOTTLE = XMaterial.HONEY_BOTTLE.parseMaterial();

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        byte packetID = event.getPacketId();

        if (packetID == PacketType.Play.Client.BLOCK_DIG) {
            GrimPlayer player = GrimAC.playerGrimHashMap.get(event.getPlayer());

            if (player == null) return;

            WrappedPacketInBlockDig dig = new WrappedPacketInBlockDig(event.getNMSPacket());

            player.compensatedWorld.packetBlockPositions.add(new BlockPlayerUpdate(dig.getBlockPosition(), player.packetStateData.packetLastTransactionReceived));

            WrappedPacketInBlockDig.PlayerDigType type = dig.getDigType();
            if ((type == WrappedPacketInBlockDig.PlayerDigType.DROP_ALL_ITEMS && player.packetStateData.eatingHand == Hand.MAIN_HAND) ||
                    type == WrappedPacketInBlockDig.PlayerDigType.RELEASE_USE_ITEM ||
                    type == WrappedPacketInBlockDig.PlayerDigType.SWAP_ITEM_WITH_OFFHAND) {

                player.packetStateData.slowedByUsingItem = AlmostBoolean.FALSE;

                if (XMaterial.supports(13)) {
                    ItemStack main = player.bukkitPlayer.getInventory().getItemInMainHand();
                    ItemStack off = player.bukkitPlayer.getInventory().getItemInOffHand();

                    int j = 0;
                    if (main.getType() == TRIDENT) {
                        j = main.getEnchantmentLevel(Enchantment.RIPTIDE);
                    } else if (off.getType() == TRIDENT) {
                        j = off.getEnchantmentLevel(Enchantment.RIPTIDE);
                    }

                    if (j > 0) {
                        player.packetStateData.tryingToRiptide = true;
                    }
                }
            }
        }

        if (packetID == PacketType.Play.Client.HELD_ITEM_SLOT) {
            GrimPlayer player = GrimAC.playerGrimHashMap.get(event.getPlayer());
            if (player == null) return;

            WrappedPacketInHeldItemSlot slot = new WrappedPacketInHeldItemSlot(event.getNMSPacket());

            // Stop people from spamming the server with out of bounds exceptions
            if (slot.getCurrentSelectedSlot() > 8) return;

            player.packetStateData.lastSlotSelected = slot.getCurrentSelectedSlot();

            if (player.packetStateData.eatingHand == Hand.MAIN_HAND) {
                player.packetStateData.slowedByUsingItem = AlmostBoolean.FALSE;
            }
        }

        if (packetID == PacketType.Play.Client.USE_ITEM) {
            GrimPlayer player = GrimAC.playerGrimHashMap.get(event.getPlayer());
            if (player == null) return;

            WrappedPacketInUseItem item = new WrappedPacketInUseItem(event.getNMSPacket());

            player.compensatedWorld.packetBlockPositions.add(new BlockPlayerUpdate(item.getBlockPosition(), player.packetStateData.packetLastTransactionReceived));
        }

        if (packetID == PacketType.Play.Client.BLOCK_PLACE) {
            WrappedPacketInBlockPlace place = new WrappedPacketInBlockPlace(event.getNMSPacket());

            GrimPlayer player = GrimAC.playerGrimHashMap.get(event.getPlayer());
            if (player == null) return;

            if (XMaterial.supports(8) && player.bukkitPlayer.getGameMode() == GameMode.SPECTATOR)
                return;

            // 1.9+ use the use item packet for this
            if (ServerVersion.getVersion().isOlderThanOrEquals(ServerVersion.v_1_8))
                player.compensatedWorld.packetBlockPositions.add(new BlockPlayerUpdate(place.getBlockPosition(), player.packetStateData.packetLastTransactionReceived));

            // Design inspired by NoCheatPlus, but rewritten to be faster
            // https://github.com/Updated-NoCheatPlus/NoCheatPlus/blob/master/NCPCompatProtocolLib/src/main/java/fr/neatmonster/nocheatplus/checks/net/protocollib/NoSlow.java
            ItemStack item = place.getHand() == Hand.MAIN_HAND ? player.bukkitPlayer.getInventory().getItem(player.packetStateData.lastSlotSelected) : player.bukkitPlayer.getInventory().getItemInOffHand();
            if (item != null) {
                Material material = item.getType();
                // 1.14 and below players cannot eat in creative, exceptions are potions or milk
                if ((player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_15) ||
                        player.bukkitPlayer.getGameMode() != GameMode.CREATIVE && material.isEdible())
                        || material == POTION || material == MILK_BUCKET) {
                    // pre1.9 splash potion
                    if (ServerVersion.getVersion().isOlderThanOrEquals(ServerVersion.v_1_8) && item.getDurability() > 16384)
                        return;

                    // Eatable items that don't require any hunger to eat
                    if (material == Material.POTION || material == Material.MILK_BUCKET
                            || material == GOLDEN_APPLE || material == ENCHANTED_GOLDEN_APPLE || material == HONEY_BOTTLE) {
                        Bukkit.broadcastMessage("STARTING DIGGING! ");
                        player.packetStateData.slowedByUsingItem = AlmostBoolean.TRUE;
                        player.packetStateData.eatingHand = place.getHand();

                        return;
                    }

                    // The other items that do require it
                    if (item.getType().isEdible() && event.getPlayer().getFoodLevel() < 20) {
                        player.packetStateData.slowedByUsingItem = AlmostBoolean.TRUE;
                        player.packetStateData.eatingHand = place.getHand();

                        return;
                    }

                    // The player cannot eat this item, resync use status
                    player.packetStateData.slowedByUsingItem = AlmostBoolean.FALSE;
                }

                if (material == SHIELD) {
                    player.packetStateData.slowedByUsingItem = AlmostBoolean.TRUE;
                    player.packetStateData.eatingHand = place.getHand();

                    return;
                }

                // Avoid releasing crossbow as being seen as slowing player
                if (material == CROSSBOW) {
                    CrossbowMeta crossbowMeta = (CrossbowMeta) item.getItemMeta();
                    if (crossbowMeta != null && crossbowMeta.hasChargedProjectiles())
                        return;
                }

                // The client and server don't agree on trident status because mojang is incompetent at netcode.
                if (material == TRIDENT) {
                    if (item.getEnchantmentLevel(Enchantment.RIPTIDE) > 0)
                        player.packetStateData.slowedByUsingItem = AlmostBoolean.MAYBE;
                    else
                        player.packetStateData.slowedByUsingItem = AlmostBoolean.TRUE;
                }

                // Players in survival can't use a bow without an arrow
                // Crossbow charge checked previously
                if (material == BOW || material == CROSSBOW) {
                    player.packetStateData.slowedByUsingItem = (player.bukkitPlayer.getGameMode() == GameMode.CREATIVE ||
                            hasItem(player, ARROW) || hasItem(player, TIPPED_ARROW) || hasItem(player, SPECTRAL_ARROW)) ? AlmostBoolean.TRUE : AlmostBoolean.FALSE;
                }

                // Only 1.8 and below players can block with swords
                if (Materials.checkFlag(material, Materials.SWORD) && player.getClientVersion().isOlderThanOrEquals(ClientVersion.v_1_8)) {
                    player.packetStateData.slowedByUsingItem = AlmostBoolean.TRUE;
                }
            } else {
                player.packetStateData.slowedByUsingItem = AlmostBoolean.FALSE;
            }
        }
    }

    private boolean hasItem(GrimPlayer player, Material material) {
        return material != null && player.bukkitPlayer.getInventory().contains(material)
                || (ServerVersion.getVersion().isNewerThanOrEquals(ServerVersion.v_1_9) && (player.bukkitPlayer.getInventory().getItemInOffHand().getType() == ARROW
                || player.bukkitPlayer.getInventory().getItemInOffHand().getType() == TIPPED_ARROW
                || player.bukkitPlayer.getInventory().getItemInOffHand().getType() == SPECTRAL_ARROW));
    }
}
