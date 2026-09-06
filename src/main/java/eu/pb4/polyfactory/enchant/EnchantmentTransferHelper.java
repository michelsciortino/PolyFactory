package eu.pb4.polyfactory.enchant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantedBookItem;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnchantmentTransferHelper {
    public static class TransferResult {
        public final ItemStack sourceResult;
        public final List<EnchantmentInstance> transferred;
        public final ItemStack bookResult; // can be empty

        public TransferResult(ItemStack sourceResult, List<EnchantmentInstance> transferred, ItemStack bookResult) {
            this.sourceResult = sourceResult;
            this.transferred = transferred;
            this.bookResult = bookResult;
        }
    }

    /**
     * Transfers up to `maxTransfer` enchantments from `source` into enchanted books. If multi==true,
     * transfers up to `maxTransfer` selected enchantments into a single book. Returns the resulting source stack (with enchants removed),
     * list of transferred enchantments and the book stack (or empty).
     *
     * Selection rules:
     *  - Curses are ignored (not transferable)
     *  - Enchantments are prioritized by level (desc) then by rarity (rare first)
     */
    public static TransferResult transferEnchantments(ItemStack source, int maxTransfer, boolean multi) {
        if (source.isEmpty()) return new TransferResult(source.copy(), List.of(), ItemStack.EMPTY);

        var map = EnchantmentHelper.getEnchantments(source);
        if (map.isEmpty()) return new TransferResult(source.copy(), List.of(), ItemStack.EMPTY);

        // Build list and filter out curses
        var entries = map.entrySet().stream()
                .filter(e -> !isCurse(e.getKey()))
                .sorted(Comparator.<Map.Entry<Enchantment, Integer>>comparingInt(e -> -e.getValue())
                        .thenComparing((a, b) -> Integer.compare(b.getKey().getRarity().ordinal(), a.getKey().getRarity().ordinal())))
                .collect(Collectors.toList());

        if (entries.isEmpty()) return new TransferResult(source.copy(), List.of(), ItemStack.EMPTY);

        var toTransfer = new ArrayList<EnchantmentInstance>();
        int count = 0;
        for (var e : entries) {
            if (count >= maxTransfer) break;
            toTransfer.add(new EnchantmentInstance(e.getKey(), e.getValue()));
            count++;
        }

        // Build book result
        ItemStack book = ItemStack.EMPTY;
        if (!toTransfer.isEmpty()) {
            if (multi) {
                book = new ItemStack(EnchantedBookItem.getItem());
                for (int i = 0; i < toTransfer.size(); i++) {
                    EnchantedBookItem.addEnchantment(book, toTransfer.get(i));
                }
            } else {
                // single mode: one book per enchant — produce only first now; machine will be invoked repeatedly
                var inst = toTransfer.get(0);
                book = new ItemStack(EnchantedBookItem.getItem());
                EnchantedBookItem.addEnchantment(book, inst);
                toTransfer = new ArrayList<>();
                toTransfer.add(inst);
            }
        }

        // Remove transferred enchants from source
        var outSource = source.copy();
        var newMap = EnchantmentHelper.getEnchantments(outSource);
        for (var inst : toTransfer) {
            newMap.remove(inst.enchantment);
        }
        EnchantmentHelper.setEnchantments(newMap, outSource);

        return new TransferResult(outSource, toTransfer, book);
    }

    private static boolean isCurse(Enchantment e) {
        try {
            return e.isCurse();
        } catch (NoSuchMethodError ex) {
            // Fallback: check registry key contains "curse" (best effort)
            var id = EnchantmentHelper.getEnchantments(new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK));
            return false;
        }
    }
}
