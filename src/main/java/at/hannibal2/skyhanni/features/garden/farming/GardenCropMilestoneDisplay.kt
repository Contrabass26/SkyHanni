package at.hannibal2.skyhanni.features.garden.farming

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.GardenCropMilestones
import at.hannibal2.skyhanni.data.GardenCropMilestones.Companion.getCounter
import at.hannibal2.skyhanni.data.GardenCropMilestones.Companion.setCounter
import at.hannibal2.skyhanni.data.TitleUtils
import at.hannibal2.skyhanni.events.*
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.FarmingFortuneDisplay
import at.hannibal2.skyhanni.features.garden.GardenAPI
import at.hannibal2.skyhanni.features.garden.GardenAPI.addCropIcon
import at.hannibal2.skyhanni.features.garden.GardenAPI.getCropType
import at.hannibal2.skyhanni.features.garden.farming.GardenCropSpeed.isSpeedDataEmpty
import at.hannibal2.skyhanni.features.garden.farming.GardenCropSpeed.setSpeed
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.addAsSingletonList
import at.hannibal2.skyhanni.utils.LorenzUtils.round
import at.hannibal2.skyhanni.utils.RenderUtils.renderStringsAndItems
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.TimeUtils
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*

object GardenCropMilestoneDisplay {
    private var progressDisplay = listOf<List<Any>>()
    private var mushroomCowPerkDisplay = listOf<List<Any>>()
    private val cultivatingData = mutableMapOf<CropType, Long>()
    private val config get() = SkyHanniMod.feature.garden
    private val bestCropTime = GardenBestCropTime()

    private var lastPlaySoundTime = 0L
    private var needsInventory = false

    @SubscribeEvent
    fun onChatMessage(event: LorenzChatEvent) {
        if (!isEnabled()) return
        // TODO remove this once hypixel counts 64x pumpkin drops to cultivating
        if (event.message == "§a§lUNCOMMON DROP! §r§eDicer dropped §r§f64x §r§fPumpkin§r§e!") {
            CropType.PUMPKIN.setCounter(CropType.PUMPKIN.getCounter() + 64)
        }
    }

    @SubscribeEvent
    fun onRenderOverlay(event: GuiRenderEvent.GameOverlayRenderEvent) {
        if (!isEnabled()) return
        if (GardenAPI.hideExtraGuis()) return

        config.cropMilestoneProgressDisplayPos.renderStringsAndItems(
            progressDisplay,
            posLabel = "Crop Milestone Progress"
        )

        if (config.cropMilestoneMushroomPetPerkEnabled) {
            config.cropMilestoneMushroomPetPerkPos.renderStringsAndItems(
                mushroomCowPerkDisplay,
                posLabel = "Mushroom Cow Perk"
            )
        }

        if (config.cropMilestoneBestDisplay) {
            config.cropMilestoneNextDisplayPos.renderStringsAndItems(bestCropTime.display, posLabel = "Best Crop Time")
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onProfileJoin(event: ProfileJoinEvent) {
        if (GardenCropMilestones.cropCounter.values.sum() == 0L) {
            needsInventory = true
        }
    }

    @SubscribeEvent
    fun onCropMilestoneUpdate(event: CropMilestoneUpdateEvent) {
        needsInventory = false
        GardenBestCropTime.updateTimeTillNextCrop()
        update()
    }

    @SubscribeEvent
    fun onOwnInventoryItemUpdate(event: OwnInventorItemUpdateEvent) {
        if (!GardenAPI.inGarden()) return

        try {
            val item = event.itemStack
            val counter = GardenAPI.readCounter(item)
            if (counter == -1L) return
            val crop = item.getCropType() ?: return
            if (cultivatingData.containsKey(crop)) {
                val old = cultivatingData[crop]!!
                val addedCounter = (counter - old).toInt()

                if (GardenCropMilestones.cropCounter.isEmpty()) {
                    for (innerCrop in CropType.values()) {
                        innerCrop.setCounter(0)
                    }
                }
                if (isSpeedDataEmpty()) {
                    for (cropType in CropType.values()) {
                        cropType.setSpeed(-1)
                    }
                }
                EliteFarmingWeight.addCrop(crop, addedCounter)
                update()
                crop.setCounter(
                    crop.getCounter() + if (GardenCropSpeed.finneganPerkActive()) {
                        (addedCounter.toDouble() * 0.8).toInt()
                    } else addedCounter
                )
            }
            cultivatingData[crop] = counter
        } catch (e: Throwable) {
            LorenzUtils.error("[SkyHanni] Error in OwnInventorItemUpdateEvent")
            e.printStackTrace()
        }
    }

    fun update() {
        progressDisplay = emptyList()
        mushroomCowPerkDisplay = emptyList()
        bestCropTime.display = emptyList()
        val currentCrop = GardenAPI.getCurrentlyFarmedCrop()
        if (GardenCropMilestones.cropCounter.isEmpty()) {
            LorenzUtils.debug("Garden Crop Milestone Display: crop counter data not yet loaded!")
            return
        }
        currentCrop?.let {
            progressDisplay = drawProgressDisplay(it, it.getCounter())
        }

        if (config.cropMilestoneBestDisplay) {
            if (config.cropMilestoneBestAlwaysOn || currentCrop != null) {
                bestCropTime.display = bestCropTime.drawBestDisplay(currentCrop)
            }
        }
    }

    private fun drawProgressDisplay(crop: CropType, counter: Long): MutableList<List<Any>> {
        val lineMap = HashMap<Int, List<Any>>()
        lineMap[0] = Collections.singletonList("§6Crop Milestones")

        val currentTier = GardenCropMilestones.getTierForCrops(counter)
        val nextTier = currentTier + 1

        val list = mutableListOf<Any>()
        list.addCropIcon(crop)
        list.add("§7" + crop.cropName + " Tier $nextTier")
        lineMap[1] = list

        val cropsForCurrentTier = GardenCropMilestones.getCropsForTier(currentTier)
        val cropsForNextTier = GardenCropMilestones.getCropsForTier(nextTier)

        val have = counter - cropsForCurrentTier
        val need = cropsForNextTier - cropsForCurrentTier

        val haveFormat = LorenzUtils.formatInteger(have)
        val needFormat = LorenzUtils.formatInteger(need)
        lineMap[2] = Collections.singletonList("§e$haveFormat§8/§e$needFormat")

        val farmingFortune = FarmingFortuneDisplay.getCurrentFarmingFortune(true)
        val speed = GardenCropSpeed.averageBlocksPerSecond
        val farmingFortuneSpeed = (farmingFortune * crop.baseDrops * speed / 100).round(1).toInt()

        if (farmingFortuneSpeed > 0) {
            crop.setSpeed(farmingFortuneSpeed)
            val missing = need - have
            val missingTimeSeconds = missing / farmingFortuneSpeed
            val millis = missingTimeSeconds * 1000
            GardenBestCropTime.timeTillNextCrop[crop] = millis
            val duration = TimeUtils.formatDuration(millis)
            if (config.cropMilestoneWarnClose) {
                if (millis < 5_900) {
                    if (System.currentTimeMillis() > lastPlaySoundTime + 1_000) {
                        lastPlaySoundTime = System.currentTimeMillis()
                        SoundUtils.playBeepSound()
                    }
                    if (!needsInventory) {
                        TitleUtils.sendTitle("§b${crop.cropName} $nextTier in $duration", 1_500)
                    }
                }
            }
            val speedText = "§7In §b$duration"
            lineMap[3] = Collections.singletonList(speedText)
            GardenAPI.itemInHand?.let {
                if (GardenAPI.readCounter(it) == -1L) {
                    lineMap[3] = listOf(speedText, " §7Inaccurate!")
                }
            }

            val format = LorenzUtils.formatInteger(farmingFortuneSpeed * 60)
            lineMap[4] = Collections.singletonList("§7Crops/Minute§8: §e$format")
            val formatBps = LorenzUtils.formatDouble(speed, config.blocksBrokenPrecision)
            lineMap[5] = Collections.singletonList("§7Blocks/Second§8: §e$formatBps")
        }

        val percentageFormat = LorenzUtils.formatPercentage(have.toDouble() / need.toDouble())
        lineMap[6] = Collections.singletonList("§7Percentage: §e$percentageFormat")

        if (GardenAPI.mushroomCowPet && crop != CropType.MUSHROOM) {
            addMushroomCowData()
        }

        return formatDisplay(lineMap)
    }

    private fun formatDisplay(lineMap: HashMap<Int, List<Any>>): MutableList<List<Any>> {
        val newList = mutableListOf<List<Any>>()
        for (index in config.cropMilestoneText) {
            lineMap[index]?.let {
                newList.add(it)
            }
        }

        if (needsInventory) {
            newList.addAsSingletonList("§cOpen §e/cropmilestones §cto update!")
        }

        return newList
    }

    private fun addMushroomCowData() {
        val lineMap = HashMap<Int, List<Any>>()
        val counter = CropType.MUSHROOM.getCounter()

        val currentTier = GardenCropMilestones.getTierForCrops(counter)
        val nextTier = currentTier + 1

        val cropsForCurrentTier = GardenCropMilestones.getCropsForTier(currentTier)
        val cropsForNextTier = GardenCropMilestones.getCropsForTier(nextTier)

        val have = counter - cropsForCurrentTier
        val need = cropsForNextTier - cropsForCurrentTier

        val haveFormat = LorenzUtils.formatInteger(have)
        val needFormat = LorenzUtils.formatInteger(need)

        val missing = need - have

        lineMap[0] = Collections.singletonList("§6Mooshroom Cow Perk")

        val list = mutableListOf<Any>()
        list.addCropIcon(CropType.MUSHROOM)
        list.add("§7Mushroom Tier $nextTier")
        lineMap[1] = list

        lineMap[2] = Collections.singletonList("§e$haveFormat§8/§e$needFormat")

        val speed = GardenCropSpeed.averageBlocksPerSecond
        if (speed != 0.0) {
            val blocksPerSecond = speed * (GardenAPI.getCurrentlyFarmedCrop()?.multiplier ?: 1)

            val missingTimeSeconds = missing / blocksPerSecond
            val millis = missingTimeSeconds * 1000
            val duration = TimeUtils.formatDuration(millis.toLong())
            lineMap[3] = Collections.singletonList("§7In §b$duration")
        }

        val percentageFormat = LorenzUtils.formatPercentage(have.toDouble() / need.toDouble())
        lineMap[4] = Collections.singletonList("§7Percentage: §e$percentageFormat")

        val newList = mutableListOf<List<Any>>()
        for (index in config.cropMilestoneMushroomPetPerkText) {
            lineMap[index]?.let {
                newList.add(it)
            }
        }
        mushroomCowPerkDisplay = newList
    }

    private fun isEnabled() = GardenAPI.inGarden() && config.cropMilestoneProgress
}
