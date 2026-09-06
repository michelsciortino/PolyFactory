package eu.pb4.polyfactory.enchant;

import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
    *  - Enchantments are prioritized by level (desc)
     */
    public static TransferResult transferEnchantments(ItemStack source, int maxTransfer, boolean multi) {
        if (source.isEmpty()) return new TransferResult(source.copy(), List.of(), ItemStack.EMPTY);

        var enchantments = source.getEnchantments();
        if (enchantments.isEmpty()) return new TransferResult(source.copy(), List.of(), ItemStack.EMPTY);

        // Build list and filter out curses
        var entries = enchantments.entrySet().stream()
                .filter(e -> !isCurse(e.getKey()))
                .sorted(Comparator.comparingInt((it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> e) -> -e.getIntValue()))
                .collect(Collectors.toList());

        if (entries.isEmpty()) return new TransferResult(source.copy(), List.of(), ItemStack.EMPTY);

        var toTransfer = new ArrayList<EnchantmentInstance>();
        int count = 0;
        for (var e : entries) {
            if (count >= maxTransfer) break;
            toTransfer.add(new EnchantmentInstance(e.getKey(), e.getIntValue()));
            count++;
        }

        // Build book result
        ItemStack book = ItemStack.EMPTY;
        if (!toTransfer.isEmpty()) {
            if (multi) {
                book = Items.ENCHANTED_BOOK.getDefaultInstance();
                for (var transfer : toTransfer) {
                    book.enchant(transfer.enchantment(), transfer.level());
                }
            } else {
                // single mode: one book per enchant — produce only first now; machine will be invoked repeatedly
                var inst = toTransfer.get(0);
                book = EnchantmentHelper.createBook(inst);
                toTransfer = new ArrayList<>();
                toTransfer.add(inst);
            }
        }

        // Remove transferred enchants from source
        var outSource = source.copy();
        Set<Holder<Enchantment>> remove = toTransfer.stream().map(EnchantmentInstance::enchantment).collect(Collectors.toSet());
        EnchantmentHelper.updateEnchantments(outSource, mutable -> mutable.removeIf(remove::contains));

        return new TransferResult(outSource, toTransfer, book);
    }

    private static boolean isCurse(Holder<Enchantment> enchantment) {
        return enchantment.is(EnchantmentTags.CURSE);
    }
}
