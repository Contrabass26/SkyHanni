package at.hannibal2.skyhanni.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.utils.NEUItems
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiEditSign
import org.lwjgl.input.Keyboard


object ChatPeek {
    @JvmStatic
    fun peek(): Boolean {
        val key = SkyHanniMod.feature.chat.peekChat

        if (Minecraft.getMinecraft().thePlayer == null) return false
        if (key <= Keyboard.KEY_NONE) return false
        if (Minecraft.getMinecraft().currentScreen is GuiEditSign) return false

        if (NEUItems.neuHasFocus()) return false

        return Keyboard.isKeyDown(key)
    }
}