package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;

public class AutoTotemModule extends Module {

    public enum TotemSlot  { OFFHAND, SLOT_1, SLOT_2, SLOT_3, SLOT_4,
                             SLOT_5,  SLOT_6, SLOT_7, SLOT_8, SLOT_9 }
    public enum SearchMode { INVENTORY, HOTBAR, BOTH }

    // ── Slot ──────────────────────────────────────────────────────────────────
    private final Setting<TotemSlot>  totemSlot    = enumSetting("Slot",       TotemSlot.OFFHAND);
    private final Setting<SearchMode> searchMode   = enumSetting("SearchMode", SearchMode.BOTH);

    // ── Health threshold ──────────────────────────────────────────────────────
    private final Setting<Boolean>    hpThreshold  = bool("HPThreshold",  false);
    private final Setting<Double>     threshold    = number("Threshold",   10.0, 1.0, 20.0);

    // ── Behaviour ─────────────────────────────────────────────────────────────
    private final Setting<Boolean>    notify       = bool("Notify",        true);
    private final Setting<Boolean>    closeInv     = bool("CloseInv",      true);
    private final Setting<Integer>    delay        = intSetting("Delay",   1, 0, 10);
    private final Setting<Boolean>    keepOne      = bool("KeepOne",       true); // keep 1 totem in inv
    private final Setting<Boolean>    countDisplay = bool("CountDisplay",  true); // show totem count

    private int  tickTimer    = 0;
    private int  lastCount    = -1;
    private boolean wasOpen   = false;

    public AutoTotemModule() {
        super("AutoTotem", "Automatically moves totems to offhand or a chosen slot.", Category.COMBAT);
    }

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        tickTimer++;
        if (tickTimer < delay.getValue()) return;
        tickTimer = 0;

        // HP threshold check
        if (hpThreshold.getValue()) {
            if (mc.player.getHealth() > threshold.getValue()) return;
        }

        // Count totems so HUD can display it
        int count = countTotems();
        if (countDisplay.getValue() && count != lastCount) {
            lastCount = count;
        }

        // Check if the target slot already has a totem
        if (hasTotemInTargetSlot()) return;

        // Find a totem in inventory
        int totemInvSlot = findTotem();
        if (totemInvSlot == -1) return;

        moveTotem(totemInvSlot);
    }

    // ─── Slot resolution ──────────────────────────────────────────────────────

    // Returns the raw inventory index of the target slot
    private int getTargetInventoryIndex() {
        switch (totemSlot.getValue()) {
            case OFFHAND: return 40; // MC offhand slot index
            case SLOT_1:  return 36;
            case SLOT_2:  return 37;
            case SLOT_3:  return 38;
            case SLOT_4:  return 39;
            case SLOT_5:  return 40; // same as offhand if both selected
            case SLOT_6:  return 9;
            case SLOT_7:  return 10;
            case SLOT_8:  return 11;
            case SLOT_9:  return 12;
            default:      return 40;
        }
    }

    // Hotbar slot 0-8 for hotbar swaps
    private int getHotbarSlot() {
        switch (totemSlot.getValue()) {
            case SLOT_1: return 0;
            case SLOT_2: return 1;
            case SLOT_3: return 2;
            case SLOT_4: return 3;
            case SLOT_5: return 4;
            case SLOT_6: return 5;
            case SLOT_7: return 6;
            case SLOT_8: return 7;
            case SLOT_9: return 8;
            default:     return -1; // offhand handled separately
        }
    }

    private boolean isOffhand() {
        return totemSlot.getValue() == TotemSlot.OFFHAND;
    }

    // ─── Totem check ──────────────────────────────────────────────────────────

    private boolean hasTotemInTargetSlot() {
        if (isOffhand()) {
            return mc.player.getHeldItemOffhand().getItem() == Items.TOTEM_OF_UNDYING;
        }

        int hotbar = getHotbarSlot();
        if (hotbar == -1) return false;

        ItemStack stack = mc.player.inventory.getStackInSlot(hotbar);
        return !stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    // ─── Find totem in inventory ───────────────────────────────────────────────

    private int findTotem() {
        // Inventory slots 9-35 (main inventory)
        if (searchMode.getValue() == SearchMode.INVENTORY
         || searchMode.getValue() == SearchMode.BOTH) {
            for (int i = 9; i <= 35; i++) {
                ItemStack stack = mc.player.inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                    // If keepOne, make sure there's more than 1 total
                    if (keepOne.getValue() && countTotems() <= 1) continue;
                    return i;
                }
            }
        }

        // Hotbar slots 0-8 (inventory index 36-44)
        if (searchMode.getValue() == SearchMode.HOTBAR
         || searchMode.getValue() == SearchMode.BOTH) {
            for (int i = 0; i < 9; i++) {
                // Skip the target hotbar slot itself
                if (!isOffhand() && i == getHotbarSlot()) continue;

                ItemStack stack = mc.player.inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                    if (keepOne.getValue() && countTotems() <= 1) continue;
                    return i + 36; // convert to inventory index
                }
            }
        }

        return -1;
    }

    // ─── Move totem to target slot ────────────────────────────────────────────

    private void moveTotem(int fromInvSlot) {
        // Open inventory silently if needed
        wasOpen = mc.currentScreen instanceof GuiInventory;

        if (!wasOpen) {
            mc.player.connection.sendPacket(new CPacketEntityAction(
                mc.player, CPacketEntityAction.Action.OPEN_INVENTORY
            ));
        }

        if (isOffhand()) {
            moveToOffhand(fromInvSlot);
        } else {
            moveToHotbarSlot(fromInvSlot);
        }

        // Close if we opened it
        if (closeInv.getValue() && !wasOpen) {
            mc.player.closeScreen();
        }

        if (notify.getValue()) {
            int remaining = countTotems();
            mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                new net.minecraft.util.text.TextComponentString(
                    "\u00a7e[AutoTotem]\u00a7f Totem moved. \u00a7a" + remaining + " remaining."
                ), 99901
            );
        }
    }

    // ─── Offhand move via shift-click then pickup ─────────────────────────────

    private void moveToOffhand(int fromInvSlot) {
        int containerId = mc.player.inventoryContainer.windowId;

        // Pick up the totem from source slot
        mc.playerController.windowClick(
            containerId,
            fromInvSlot,
            0,
            ClickType.PICKUP,
            mc.player
        );

        // Place into offhand slot (slot 40 in container)
        mc.playerController.windowClick(
            containerId,
            40,
            0,
            ClickType.PICKUP,
            mc.player
        );

        // If offhand had something, put it back to original slot
        ItemStack offhandPrev = mc.player.getHeldItemOffhand();
        if (!offhandPrev.isEmpty() && offhandPrev.getItem() != Items.TOTEM_OF_UNDYING) {
            mc.playerController.windowClick(
                containerId,
                fromInvSlot,
                0,
                ClickType.PICKUP,
                mc.player
            );
        }
    }

    // ─── Hotbar move via swap key (number key click) ──────────────────────────

    private void moveToHotbarSlot(int fromInvSlot) {
        int hotbar      = getHotbarSlot();
        int containerId = mc.player.inventoryContainer.windowId;

        if (hotbar == -1) return;

        // Swap directly to hotbar slot using button index
        mc.playerController.windowClick(
            containerId,
            fromInvSlot,
            hotbar,
            ClickType.SWAP,
            mc.player
        );
    }

    // ─── Count totems in full inventory ───────────────────────────────────────

    private int countTotems() {
        int count = 0;

        // Main inventory + hotbar (slots 0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) count++;
        }

        // Offhand
        if (mc.player.getHeldItemOffhand().getItem() == Items.TOTEM_OF_UNDYING) count++;

        return count;
    }

    // ─── HUD overlay (totem count) ────────────────────────────────────────────

    @Override
    public void onRender2D(float partialTicks) {
        if (!countDisplay.getValue()) return;
        if (nullCheck()) return;

        int count = countTotems();
        String text = "\u00a76Totems: \u00a7f" + count;

        // Draw in top-left — use your client's existing font renderer
        mc.fontRenderer.drawStringWithShadow(
            text,
            4,
            4 + (mc.fontRenderer.FONT_HEIGHT + 2) * getLineOffset(),
            0xFFFFFF
        );
    }

    // Override this if you want to stack it with other HUD elements
    private int getLineOffset() { return 0; }
}
