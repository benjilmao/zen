package xyz.meowing.zen.features.qol

import xyz.meowing.zen.Zen
import xyz.meowing.zen.api.ItemAPI
import xyz.meowing.zen.config.ui.ConfigUI
import xyz.meowing.zen.config.ui.types.ConfigElement
import xyz.meowing.zen.config.ui.types.ElementType
import xyz.meowing.zen.events.*
import xyz.meowing.zen.features.Feature
import xyz.meowing.zen.hud.HUDManager
import xyz.meowing.zen.utils.Render2D
import xyz.meowing.zen.utils.ItemUtils.skyblockID
import xyz.meowing.zen.utils.ItemUtils.isHolding
import xyz.meowing.zen.utils.Utils.removeFormatting
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.event.entity.player.PlayerInteractEvent

@Zen.Module
object BuffTimers : Feature("bufftimers", true) {

    private const val name = "BuffTimers"

    private const val FLARE_DURATION = 180.0 * 20
    private const val RADIANT_MANA_DURATION = 30.0 * 20
    private const val OVERFLUX_PLASMA_DURATION = 60.0 * 20

    private var ragnarock = 0.0
    private var sobh = 0.0
    private var tuba = 0.0
    private var flare = 0.0
    private var orb = 0.0

    private var ragnarockItem: ItemStack? = null
    private var sobhItem: ItemStack? = null
    private var tubaItem: ItemStack? = null
    private var flareItem: ItemStack? = null
    private var orbItem: ItemStack? = null

    data class BuffData(val item: ItemStack, val timeStr: String, val color: String)

    override fun addConfig(configUI: ConfigUI): ConfigUI{
        return configUI
            .addElement("QoL", "Buff timers", ConfigElement(
                "bufftimers",
                null,
                ElementType.Switch(false)
            ), isSectionToggle = true)
    }

    override fun initialize() {
        HUDManager.registerCustom(name, 60, 100, this::HUDEditorRender)

        register<SkyblockEvent.ItemAbilityUsed> { event ->
            when (event.ability.itemId) {
                "SWORD_OF_BAD_HEALTH" -> if (sobh <= 0) sobh = event.ability.cooldownSeconds * 20
                "WEIRD_TUBA", "WEIRDER_TUBA" -> if (tuba <= 0) tuba = event.ability.cooldownSeconds * 20
            }
        }

        register<EntityEvent.Interact> { event ->
            if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return@register
            val held = mc.thePlayer?.heldItem ?: return@register
            val id = held.skyblockID

            when {
                id.contains("FLARE") && flare <= 0 -> flare = FLARE_DURATION
                id.contains("POWER_ORB") && orb <= 0 -> orb = getOrbDuration(id)
            }
        }

        register<PacketEvent.Received> { event ->
            val p = event.packet
            if (p is net.minecraft.network.play.server.S29PacketSoundEffect) {
                if (p.soundName == "mob.wolf.howl" && p.pitch == 1.4920635f && isHolding("RAGNAROCK_AXE")) {
                    ragnarock = 400.0
                }
            }
        }

        register<ChatEvent.Receive> { event ->
            val msg = event.event.message.unformattedText.removeFormatting()
            when {
                msg.contains("You cannot use this item in the village!") && flare > 0 ->
                    flare = 0.0
                msg.contains("Your flare disappeared") && flare > 0 ->
                    flare = 0.0
                msg.contains("Your previous") && msg.contains("was removed") -> {
                    when {
                        msg.contains("Flare") && flare > 0 -> flare = FLARE_DURATION
                        msg.contains("Power Orb") && orb > 0 -> orbItem?.let {
                            orb = getOrbDuration(it.skyblockID)
                        }
                    }
                }
            }
        }

        register<TickEvent.Server> {
            if (ragnarock > 0) ragnarock--
            if (sobh > 0) sobh--
            if (tuba > 0) tuba--
            if (flare > 0) flare--
            if (orb > 0) orb--
            updateItems()
        }

        register<RenderEvent.Text> {
            if (HUDManager.isEnabled(name)) render()
        }

        register<WorldEvent.Change> {
            ragnarock = 0.0; sobh = 0.0; tuba = 0.0; flare = 0.0; orb = 0.0
            ragnarockItem = null; sobhItem = null; tubaItem = null; flareItem = null; orbItem = null
        }
    }

    private fun getOrbDuration(id: String) = when {
        id.contains("RADIANT") || id.contains("MANA_FLUX") -> RADIANT_MANA_DURATION
        id.contains("OVERFLUX") || id.contains("PLASMAFLUX") -> OVERFLUX_PLASMA_DURATION
        else -> RADIANT_MANA_DURATION
    }

    private fun updateItems() {
        val player = mc.thePlayer ?: return

        ragnarockItem = null; sobhItem = null; tubaItem = null; flareItem = null; orbItem = null

        for (slot in 0..35) {
            val stack = player.inventory.getStackInSlot(slot) ?: continue
            val id = stack.skyblockID

            when {
                id == "RAGNAROCK_AXE" && ragnarockItem == null -> ragnarockItem = stack
                id == "SWORD_OF_BAD_HEALTH" && sobhItem == null -> sobhItem = stack
                (id == "WEIRD_TUBA" || id == "WEIRDER_TUBA") && tubaItem == null -> tubaItem = stack
                id.contains("FLARE") && flareItem == null -> flareItem = stack
                id.contains("POWER_ORB") && orbItem == null -> orbItem = stack
            }
        }
    }

    private fun render() {
        val list = getActiveBuffs()
        if (list.isEmpty()) return
        drawHUD(HUDManager.getX(name), HUDManager.getY(name), HUDManager.getScale(name), false, list)
    }

    private fun HUDEditorRender(x: Float, y: Float, w: Int, h: Int, scale: Float, pt: Float, preview: Boolean) {
        updateItems()

        val previewBuffs = listOfNotNull(
            ragnarockItem?.let { BuffData(it, "20.0s", "§e") },
            sobhItem?.let { BuffData(it, "5.0s", "§c") },
            tubaItem?.let { BuffData(it, "Ready", "§7") },
            flareItem?.let { BuffData(it, "2:45", "§4") },
            orbItem?.let { BuffData(it, "45.2s", "§4") }
        )

        drawHUD(x, y, 1f, true, previewBuffs)
    }

    private fun getActiveBuffs() = listOfNotNull(
        ragnarockItem?.let { BuffData(it, if (ragnarock > 0) formatTicks(ragnarock) else "Ready", "§6") },
        sobhItem?.let { BuffData(it, if (sobh > 0) formatTicks(sobh) else "Ready", "§c") },
        tubaItem?.let { BuffData(it, if (tuba > 0) formatTicks(tuba) else "Ready", "§7") },
        flareItem?.let { BuffData(it, if (flare > 0) formatTicks(flare) else "Ready", "§4") },
        orbItem?.let { BuffData(it, if (orb > 0) formatTicks(orb) else "Ready", "§4") }
    )

    private fun formatTicks(t: Double): String {
        val seconds = t / 20.0
        return if (seconds >= 60) {
            "%d:%02d".format(seconds.toInt() / 60, seconds.toInt() % 60)
        } else {
            "%.1fs".format(seconds)
        }
    }

    private fun drawHUD(x: Float, y: Float, scale: Float, preview: Boolean, buffs: List<BuffData>) {
        val icon = 16f * scale
        val space = 2f * scale
        var yy = y

        for (b in buffs) {
            Render2D.renderItem(b.item, x, yy, scale)
            Render2D.renderString("${b.color}${b.timeStr}", x + icon + space, yy + (icon - 8f) / 2f, scale)
            yy += icon + space
        }
    }

    private fun createSBItem(id: String): ItemStack {
        val data = ItemAPI.getItemInfo(id) ?: return ItemStack(Blocks.barrier)

        val mcId = data.get("itemid").asString
        val dmg = data.get("damage").asInt

        val name = mcId.substringAfter("minecraft:")
        val item = when (name) {
            "golden_sword", "golden_axe" -> Items.golden_axe
            "wooden_sword" -> Items.wooden_sword
            "hopper" -> Item.getItemFromBlock(Blocks.hopper)
            "fireworks" -> Items.fireworks
            "skull", "player_head" -> Items.skull
            else -> Item.getItemFromBlock(Blocks.barrier)
        }

        return ItemStack(item, 1, dmg).apply {
            tagCompound = NBTTagCompound().apply {
                setTag("ExtraAttributes", NBTTagCompound().apply { setString("id", id) })
                setTag("display", NBTTagCompound().apply {
                    setString("Name", data.get("displayname").asString)
                })
            }
        }
    }
}
