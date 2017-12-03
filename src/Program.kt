import bwapi.*
import bwta.BWTA

class Program : DefaultBWListener() {

    private val mirror = Mirror()
    private var game: Game? = null
    private var self: Player? = null
    private var buildingManager: TerranBuildingManager? = null

    fun run() {
        mirror.module.setEventListener(this)
        mirror.startGame()
    }

    override fun onStart() {
        game = mirror.game
        self = game!!.self()

        game?.let {
            buildingManager = TerranBuildingManager(NewBuildingLocator(it), it)
        }



        BWTA.readMap()
        BWTA.analyze()
    }

    override fun onFrame() {
        (self as Player).units.forEach {
            buildingManager?.addWorker(it)
        }

        buildingManager?.let {
            it.buildSupplyDepot()
            game?.drawTextScreen(25, 50, it.workerCount())
        }
        game?.drawTextScreen(25, 25, "Test")
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            Program().run()
        }
    }
}