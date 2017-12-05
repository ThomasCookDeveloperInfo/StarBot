import bwapi.*
import bwta.BWTA
typealias BwapiUnit = bwapi.Unit

class Program : DefaultBWListener() {

    private val mirror = Mirror()
    private lateinit var game: Game
    private lateinit var self: Player
    private lateinit var builderManager: TerranBuilderManager
    private lateinit var resourceManager: ResourceManager

    fun run() {
        mirror.module.setEventListener(this)
        mirror.startGame()
    }

    override fun onStart() {
        game = mirror.game
        self = game.self()

        builderManager = TerranBuilderManager(UnitLocator(game), game)
        resourceManager = ResourceManager()
        builderManager.setCallbacks(resourceManager)

        self.units.forEach {
            builderManager.addWorker(it)
        }

        BWTA.readMap()
        BWTA.analyze()
    }

    override fun onFrame() {
        resourceManager.minerals = self.minerals()
        resourceManager.gas = self.gas()

        if (resourceManager.canAfford(UnitType.Terran_Supply_Depot)) {
            builderManager.buildSupplyDepot()
        }
        builderManager.gatherMinerals()
        builderManager.updateRefunds()

        resourceManager.debug(game)
        game.drawTextScreen(25, 150, builderManager.workerCount())
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            Program().run()
        }
    }
}