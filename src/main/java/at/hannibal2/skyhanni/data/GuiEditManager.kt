package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.core.config.Position
import at.hannibal2.skyhanni.config.core.config.gui.GuiPositionEditor
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NEUItems
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.renderer.GlStateManager
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import java.util.*

class GuiEditManager {

    // TODO Make utils method for this
    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (!LorenzUtils.inSkyBlock) return

        Minecraft.getMinecraft().currentScreen?.let {
            if (it !is GuiInventory && it !is GuiChest && it !is GuiEditSign) return
        }

        if (!Keyboard.getEventKeyState()) return
        val key = if (Keyboard.getEventKey() == 0) Keyboard.getEventCharacter().code + 256 else Keyboard.getEventKey()
        if (SkyHanniMod.feature.gui.keyBindOpen != key) return

        if (NEUItems.neuHasFocus()) return

        if (isInGui()) return
        openGuiEditor()
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRenderOverlay(event: GuiRenderEvent.GameOverlayRenderEvent) {
        latestPositions = currentPositions.toMap()
        currentPositions.clear()
    }

    companion object {
        var currentPositions = mutableMapOf<String, Position>()
        private var latestPositions = mapOf<String, Position>()
        private var currentBorderSize = mutableMapOf<String, Pair<Int, Int>>()

        @JvmStatic
        fun add(position: Position, posLabel: String, x: Int, y: Int) {
            var name = position.internalName
            if (name == null) {
                name = if (posLabel == "none") "none " + UUID.randomUUID() else posLabel
                position.internalName = name
            }
            if (!currentPositions.containsKey(name)) {
                currentPositions[name] = position
                currentBorderSize[posLabel] = Pair(x, y)
            }
        }

        @JvmStatic
        fun openGuiEditor() {
            SkyHanniMod.screenToOpen = GuiPositionEditor(latestPositions.values.toList(), 2)
        }

        @JvmStatic
        fun renderLast() {
            if (!isInGui()) return

            GlStateManager.translate(0f, 0f, 200f)

            GuiRenderEvent.GameOverlayRenderEvent().postAndCatch()

            GlStateManager.pushMatrix()
            GlStateManager.enableDepth()
            GuiRenderEvent.ChestBackgroundRenderEvent().postAndCatch()
            GlStateManager.popMatrix()

            GlStateManager.translate(0f, 0f, -200f)
        }

        fun isInGui() = Minecraft.getMinecraft().currentScreen is GuiPositionEditor

        fun Position.getDummySize(random: Boolean = false): Vector2i {
            if (random) {
                return Vector2i(5, 5)
            } else {
                val (x, y) = currentBorderSize[internalName] ?: return Vector2i(1, 1)
                return Vector2i(x, y)
            }
        }

        fun Position.getAbsX() = getAbsX0(getDummySize(true).x)

        fun Position.getAbsY() = getAbsY0(getDummySize(true).y)
    }
}

class Vector2i(val x: Int, val y: Int)