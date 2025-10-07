package com.yourname.menudumper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * MC Menu Dumper (client-side, Fabric 1.21.x)
 * - Herhangi bir envanter/menü (HandledScreen) açıldığında menünün adını ve
 *   menüye ait slotlardaki item adlarını, adetlerini ve lore satırlarını
 *   masaüstündeki "mc-menu.txt" dosyasına yazar.
 * - Menü açıkken içerik değişirse (adet/lore vs.) dosyayı yeniden yazar.
 */
public class MenuDumpClient implements ClientModInitializer {

    private HandledScreen<?> lastScreen = null;
    private int lastContentHash = 0;

    @Override
    public void onInitializeClient() {
        // Ekran initialize olduğunda tetiklenir → menüyü dump et
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof HandledScreen<?> hs) {
                this.lastScreen = hs;
                dumpMenuToDesktop(hs, /*force*/ true);
            }
        });

        // Menü açıkken her tick kontrol et; değişiklik varsa yeniden yaz
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen instanceof HandledScreen<?> hs) {
                dumpMenuToDesktop(hs, /*force*/ false);
            } else {
                lastScreen = null;
                lastContentHash = 0;
            }
        });
    }

    private void dumpMenuToDesktop(HandledScreen<?> hs, boolean force) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        ScreenHandler handler = hs.getScreenHandler();
        PlayerInventory playerInv = mc.player.getInventory();

        StringBuilder sb = new StringBuilder();

        // Menünün başlığı
        String menuTitle = hs.getTitle() != null ? hs.getTitle().getString() : "(unknown)";
        sb.append("=== MENU: ").append(menuTitle).append(" ===\n");

        // Menüye ait slotları gez (oyuncu envanterini hariç tutuyoruz)
        int visibleIndex = 0;
        for (Slot slot : handler.slots) {
            if (slot.inventory == playerInv) continue; // oyuncu envanterini yazma
            visibleIndex++;

            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                sb.append(String.format("[%02d] (empty)\n", visibleIndex));
                continue;
            }

            String itemName = stack.getName().getString();
            int count = stack.getCount();
            sb.append(String.format("[%02d] %s x%d\n", visibleIndex, itemName, count));

            // --- Lore (Data Components ile, 1.21.x) ---
            try {
                LoreComponent lore = stack.get(DataComponentTypes.LORE);
                if (lore != null) {
                    for (Text line : lore.lines()) {
                        String s = line.getString();
                        if (!s.isEmpty()) {
                            sb.append("   - ").append(s).append("\n");
                        }
                    }
                }
            } catch (Throwable t) {
                sb.append("   (lore okunamadı: ").append(t.getClass().getSimpleName()).append(")\n");
            }
        }

        String snapshot = sb.toString();
        int contentHash = snapshot.hashCode();

        if (force || contentHash != lastContentHash) {
            writeToDesktop(snapshot);
            lastContentHash = contentHash;
        }
    }

    private void writeToDesktop(String text) {
        try {
            String userHome = System.getProperty("user.home");
            File outFile = new File(userHome + File.separator + "Desktop", "mc-menu.txt");
            try (FileWriter fw = new FileWriter(outFile, false)) { // false => üzerine yaz
                fw.write(text);
            }
            System.out.println("[MC Menu Dumper] Yazıldı: " + outFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
