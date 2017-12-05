import bwapi.Game
import bwapi.TilePosition
import bwapi.UnitType

fun BwapiUnit.isWorker() : Boolean {
    return this.type != UnitType.Terran_SCV
            || this.type != UnitType.Zerg_Drone
            || this.type != UnitType.Protoss_Probe
}

fun BwapiUnit.isBuilding() : Boolean {
    return this.type != UnitType.Terran_SCV &&
            this.type != UnitType.Terran_Dropship &&
            this.type != UnitType.Terran_Battlecruiser
}

class ResourceManager : TerranBuilderManager.IListener {
    private var mineralsAfterSpending = 0
    private var gasAfterSpending = 0

    var minerals = 0
        set(value) {
            val delta = value - field
            mineralsAfterSpending += delta
            field = value
        }

    var gas = 0
        set(value) {
            val delta = value - field
            gasAfterSpending += delta
            field = value
        }

    fun canAfford(unitType: UnitType) : Boolean {
        return unitType.mineralPrice() <= mineralsAfterSpending
                && unitType.gasPrice() <= gasAfterSpending
    }

    override fun spendResources(spentMinerals: Int, spentGas: Int) {
        this.mineralsAfterSpending -= spentMinerals
        this.gasAfterSpending -= spentGas
    }

    override fun refundResources(refundedMinerals: Int, refundedGas: Int) {
        this.mineralsAfterSpending += refundedMinerals
        this.gasAfterSpending += refundedGas
    }

    fun debug(game: Game) {
        game.drawTextScreen(25, 25, "minerals: $minerals")
        game.drawTextScreen(25, 50, "minerals after spending: $mineralsAfterSpending")
        game.drawTextScreen(25, 75, "gas: $gas")
        game.drawTextScreen(25, 125, "gas after spending: $gasAfterSpending")
    }
}

class TerranBuilderManager(private val unitLocator: UnitLocator, private val game: Game) {
    interface IListener {
        fun spendResources(minerals: Int, gas: Int)
        fun refundResources(minerals: Int, gas: Int)
    }

    private var callbacks: IListener? = null
    fun setCallbacks(implementor: IListener?) {
        callbacks = implementor
    }

    private val workers = mutableListOf<Builder>()
    private val supplyDepots = mutableListOf<BwapiUnit>()

    private fun hasWorker(worker: BwapiUnit) : Boolean {
        var hasWorker = false
        workers.forEach {
            if (it.unit.id == worker.id) {
                hasWorker = true
                return@forEach
            }
        }
        return hasWorker
    }

    fun workerCount() = "${workers.size} workers"

    fun addWorker(worker: BwapiUnit) : Boolean {
        if (!worker.isWorker())
            throw IllegalStateException("You cannot add a unit of type ${worker.type}" +
                    " to the TerranBuildingManager's workers")

        if (!hasWorker(worker)) {
            var homeBuilding: BwapiUnit? = null
            game.self().units.forEach {
                if (it.type == UnitType.Terran_Command_Center) {
                    homeBuilding = it
                }
            }

            return workers.add(Builder(worker, homeBuilding ?: return false))
        } else {
            return false
        }
    }

    fun buildSupplyDepot() : Boolean {
        val worker = workers.firstOrNull {
            !it.unit.isConstructing
        } ?: return false

        val location =
                unitLocator
                        .findSuitablePositionFor(UnitType.Terran_Supply_Depot, worker)

        if (location === null)
            return false

        val built = worker.unit.build(UnitType.Terran_Supply_Depot, location)
        if (built) {
            callbacks?.spendResources(UnitType.Terran_Supply_Depot.mineralPrice(),
                    UnitType.Terran_Supply_Depot.gasPrice())

            val supplyDepot = worker.unit.target
            worker.buildingTask = supplyDepot.type
            supplyDepots.add(supplyDepot)
        }

        return built
    }

    fun gatherMinerals() {
        workers.forEach {
            if(!it.unit.isGatheringMinerals
                    && !it.unit.isGatheringGas
                    && !it.buildingStarted()) {
                it.unit.gather(unitLocator.findMineralFor(it))
            }
        }
    }

    fun updateRefunds() {
        workers.forEach { worker ->
            if (worker.buildingStarted()) {
                worker.buildingTask?.let {
                    callbacks?.refundResources(it.mineralPrice(), it.gasPrice())
                }
                worker.buildingTask = null
            }
        }
    }
}

class UnitLocator(private val game: Game) {

    fun findMineralFor(builder: Builder) : BwapiUnit? {
        val buildersLocation = builder.unit.position
        val mineralsInGame = game.minerals

        var smallestDistance = Int.MAX_VALUE
        var closest = -1
        for (i in 0..mineralsInGame.size - 1) {
            val approxDistance = buildersLocation
                    .getApproxDistance(mineralsInGame[i].position)

            if (approxDistance < smallestDistance) {
                smallestDistance = approxDistance
                closest = i
            }
        }

        if (closest != -1) {
            return mineralsInGame[closest]
        }
        return null
    }

    fun findSuitablePositionFor(buildingType: UnitType, withBuilder: Builder) : TilePosition? {
        val ret: TilePosition? = null
        var maxDist = 3
        val stopDist = 40
        val aroundTile = withBuilder.home.tilePosition
        val builderUnit = withBuilder.unit

        // Refinery, Assimilator, Extractor
        if (buildingType.isRefinery) {
            for (n in game.neutral().units) {
                if (n.type === UnitType.Resource_Vespene_Geyser &&
                        Math.abs(n.tilePosition.x - aroundTile.x) < stopDist &&
                        Math.abs(n.tilePosition.y - aroundTile.y) < stopDist)
                    return n.tilePosition
            }
        }

        while (maxDist < stopDist && ret == null) {
            for (i in aroundTile.x - maxDist..aroundTile.x + maxDist) {
                for (j in aroundTile.y - maxDist..aroundTile.y + maxDist) {
                    if (game.canBuildHere(TilePosition(i, j), buildingType, builderUnit, false)) {
                        // units that are blocking the tile
                        var unitsInWay = false
                        for (u in game.allUnits) {
                            if (u.id == builderUnit.id) continue
                            if (Math.abs(u.tilePosition.x - i) < 4 && Math.abs(u.tilePosition.y - j) < 4) unitsInWay = true
                        }
                        if (!unitsInWay) {
                            return TilePosition(i, j)
                        }
                        // creep for Zerg
                        if (buildingType.requiresCreep()) {
                            var creepMissing = false
                            for (k in i..i + buildingType.tileWidth()) {
                                for (l in j..j + buildingType.tileHeight()) {
                                    if (!game.hasCreep(k, l)) creepMissing = true
                                    break
                                }
                            }
                            if (creepMissing) continue
                        }
                    }
                }
            }
            maxDist += 2
        }

        return ret
    }
}

class Builder(var unit: BwapiUnit, var home: BwapiUnit) {

    var buildingTask: UnitType? = null

    init {
        if (!unit.isWorker())
            throw IllegalStateException("A builder cannot be created with unit type ${unit.type}")
        if (!home.isBuilding())
            throw IllegalStateException("A builder cannot be created with home type ${home.type}")
    }

    fun migrateToNewHome(newHome: BwapiUnit) : Boolean {
        if (!newHome.isBuilding())
            throw IllegalStateException("A builder cannot migrate to a unit with type ${newHome.type}")
        return false
    }

    fun buildingStarted() : Boolean {
        return buildingTask !== null && unit.isConstructing
    }
}