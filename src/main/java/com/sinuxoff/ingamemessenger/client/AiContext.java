package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Game context for Gemini, rebuilt on the render thread for every request from what's actually there: game and mods,
 * the player's state and inventory, location, world, biome, time, weather, what they look at, nearby and online players.
 */
final class AiContext {
    private AiContext() {
    }

    static String system() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        String lang = mc.getLanguageManager().getSelected();
        StringBuilder b = new StringBuilder();
        b.append("You are Gemini, an assistant inside the Minecraft chat (the InGame Messenger mod), talking with the player ")
                .append(p != null ? p.getGameProfile().getName() : "?").append(".\n")
                .append("Below is live data from the player's game. Use it only when the player's question needs it ")
                .append("(their health, items, where they are, mods…); otherwise just talk normally. ")
                .append("Never bring the data up on your own, never comment on their inventory or surroundings unprompted, ")
                .append("and never say that you can see their game or that you were given data. ")
                .append("If they ask about something the data doesn't show, say you don't know rather than guess. ")
                .append("Sound like a friendly person in a chat, not like a report. ")
                .append("Answer in the player's language (their game is set to ").append(lang).append("), concisely unless asked for detail. ")
                .append("Formatting: plain text; you may use **bold**, *italic*, `code` and lines starting with \"- \"; no tables, no headings, no links.\n\n");
        game(b, mc);
        if (p != null && mc.level != null) {
            player(b, mc);
        }
        return b.toString();
    }

    private static void game(StringBuilder b, Minecraft mc) {
        b.append("== Game ==\n");
        b.append("Minecraft ").append(com.sinuxoff.ingamemessenger.ServerCompat.gameVersion()).append(", ")
                .append(com.sinuxoff.ingamemessenger.Platform.get().loader()).append('\n');
        StringBuilder names = new StringBuilder();
        int n = 0;
        for (String m : com.sinuxoff.ingamemessenger.Platform.get().mods()) {
            n++;
            if (names.length() < 6000) {
                names.append(names.length() > 0 ? ", " : "").append(m);
            }
        }
        b.append(n).append(" mods installed: ").append(names).append('\n');
        var server = mc.getCurrentServer();
        b.append(mc.hasSingleplayerServer() ? "Singleplayer world" : "Multiplayer server" + (server != null ? " \"" + server.name + "\"" : ""))
                .append('\n');
        var conn = mc.getConnection();
        if (conn != null) {
            StringBuilder on = new StringBuilder();
            for (var info : conn.getOnlinePlayers()) {
                on.append(on.length() > 0 ? ", " : "").append(info.getProfile().getName());
            }
            b.append("Players online (").append(conn.getOnlinePlayers().size()).append("): ").append(on).append('\n');
            var me = mc.player != null ? conn.getPlayerInfo(mc.player.getUUID()) : null;
            if (me != null) {
                b.append("Ping: ").append(me.getLatency()).append(" ms\n");
            }
        }
    }

    private static void player(StringBuilder b, Minecraft mc) {
        var p = mc.player;
        var level = mc.level;
        BlockPos pos = p.blockPosition();
        b.append("\n== Player ==\n");
        b.append(String.format(java.util.Locale.ROOT, "Health %.1f/%.0f, armor %d, food %d/20 (saturation %.1f), XP level %d, air %d/%d\n",
                p.getHealth(), p.getMaxHealth(), p.getArmorValue(), p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel(),
                p.experienceLevel, p.getAirSupply(), p.getMaxAirSupply()));
        b.append("Game mode: ").append(mc.gameMode != null ? mc.gameMode.getPlayerMode().getName() : "?")
                .append(", difficulty: ").append(level.getDifficulty().getSerializedName()).append('\n');
        if (!p.getActiveEffects().isEmpty()) {
            StringBuilder e = new StringBuilder();
            for (var eff : p.getActiveEffects()) {
                e.append(e.length() > 0 ? ", " : "").append(eff.getEffect().value().getDisplayName().getString())
                        .append(eff.getAmplifier() > 0 ? " " + (eff.getAmplifier() + 1) : "")
                        .append(eff.isInfiniteDuration() ? "" : " (" + eff.getDuration() / 20 + "s)");
            }
            b.append("Effects: ").append(e).append('\n');
        }
        b.append("\n== Place ==\n");
        b.append("Position: ").append(pos.getX()).append(' ').append(pos.getY()).append(' ').append(pos.getZ())
                .append(", dimension ").append(level.dimension().location()).append('\n');
        level.getBiome(pos).unwrapKey().ifPresent(k -> b.append("Biome: ").append(k.location()).append('\n'));
        long t = Compat.dayTime(level) % 24000;
        int hours = (int) ((t / 1000 + 6) % 24), mins = (int) (t % 1000 * 60 / 1000);
        b.append(String.format(java.util.Locale.ROOT, "Time: day %d, %02d:%02d (%s), weather: %s\n", Compat.dayTime(level) / 24000 + 1, hours, mins,
                t < 12300 || t > 23850 ? "day" : "night", level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear"));
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            var st = level.getBlockState(bh.getBlockPos());
            b.append("Looking at block: ").append(st.getBlock().getName().getString()).append(" (")
                    .append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock())).append(") at ")
                    .append(bh.getBlockPos().toShortString()).append('\n');
        } else if (hit instanceof EntityHitResult eh) {
            b.append("Looking at: ").append(eh.getEntity().getName().getString()).append('\n');
        }
        Map<String, Integer> near = new TreeMap<>();
        for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(24))) {
            near.merge(e.getName().getString(), 1, Integer::sum);
        }
        if (!near.isEmpty()) {
            StringBuilder nb = new StringBuilder();
            near.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(15)
                    .forEach(en -> nb.append(nb.length() > 0 ? ", " : "").append(en.getKey()).append(en.getValue() > 1 ? " x" + en.getValue() : ""));
            b.append("Nearby (24 blocks): ").append(nb).append('\n');
        }
        b.append("\n== Items ==\n");
        b.append("Main hand: ").append(item(p.getMainHandItem())).append("; off hand: ").append(item(p.getOffhandItem())).append('\n');
        StringBuilder armor = new StringBuilder();
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = p.getItemBySlot(s);
            if (!st.isEmpty()) {
                armor.append(armor.length() > 0 ? ", " : "").append(item(st));
            }
        }
        b.append("Armor: ").append(armor.length() > 0 ? armor : "none").append('\n');
        Map<String, Integer> inv = new LinkedHashMap<>();
        for (ItemStack st : Compat.items(p)) {
            if (!st.isEmpty()) {
                inv.merge(st.getHoverName().getString(), st.getCount(), Integer::sum);
            }
        }
        StringBuilder ib = new StringBuilder();
        inv.forEach((k, v) -> ib.append(ib.length() > 0 ? ", " : "").append(k).append(" x").append(v));
        b.append("Inventory: ").append(ib.length() > 0 ? ib : "empty").append('\n');
    }

    /** "Name xN", plus durability if it has any. */
    private static String item(ItemStack s) {
        if (s.isEmpty()) {
            return "nothing";
        }
        String out = s.getHoverName().getString() + (s.getCount() > 1 ? " x" + s.getCount() : "");
        if (s.isDamageableItem()) {
            out += " (" + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage() + " durability)";
        }
        return out;
    }
}
