import bwapi.Game
import bwapi.TilePosition
import bwapi.UnitType

fun bwapi.Unit.isWorker() : Boolean {
    return this.type != UnitType.Terran_SCV
            || this.type != UnitType.Zerg_Drone
            || this.type != UnitType.Protoss_Probe
}

fun bwapi.Unit.isBuilding() : Boolean {
    return this.type != UnitType.Terran_SCV &&
            this.type != UnitType.Terran_Dropship &&
            this.type != UnitType.Terran_Battlecruiser
}

class TerranBuildingManager(private val newBuildingLocator: NewBuildingLocator, private val game: Game) {
    private val workers = mutableListOf<Builder>()
    private val supplyDepots = mutableListOf<bwapi.Unit>()

    private fun hasWorker(worker: bwapi.Unit) : Boolean {
        var hasWorker = false
        workers.forEach {
            if (it.unit?.id == worker.id) {
                hasWorker = true
                return@forEach
            }
        }
        return hasWorker
    }

    fun workerCount() = "${workers.size} workers"

    fun addWorker(worker: bwapi.Unit) : Boolean {
        if (!worker.isWorker())
            throw IllegalStateException("You cannot add a unit of type ${worker.type}" +
                    " to the TerranBuildingManager's workers")

        if (!hasWorker(worker)) {
            var homeBuilding: bwapi.Unit? = null
            game.self().units.forEach {
                if (it.type == UnitType.Terran_Command_Center) {
                    homeBuilding = it
                }
            }

            homeBuilding?.let {
                workers.add(Builder(worker, it))
                return true
            }
            return false
        } else {
            return false
        }
    }

    fun buildSupplyDepot() : Boolean {
        val worker = workers.firstOrNull {
            it.unit?.isBuilding() ?: false
        } ?: return false

        if (worker is Builder) {
            val location =
                    newBuildingLocator
                            .findSuitablePositionFor(UnitType.Terran_Supply_Depot, worker)

            if (location === null)
                return false

            worker.unit?.let {
                val built = it.build(UnitType.Terran_Supply_Depot, location)
                val supplyDepot = it.target
                supplyDepots.add(supplyDepot)
                return built
            }

            return false
        } else {
            return false
        }
    }
}

class NewBuildingLocator(private val game: Game) {

    fun findSuitablePositionFor(buildingType: UnitType, withBuilder: Builder) : TilePosition? {
        val stopDistance = 40
        var currentDistance = 3

        val worker = withBuilder.unit ?: return null
        val workersHome = withBuilder.home
        val homePosition = workersHome?.position ?: return null

        // Gas buildings
        if (buildingType.isRefinery) {
            game.neutralUnits.forEach { neutral ->
                if (neutral.type == UnitType.Resource_Vespene_Geyser
                        && Math.abs(neutral.position.x - homePosition.x) < stopDistance
                        && Math.abs(neutral.position.y - homePosition.y) < stopDistance) {
                    return neutral.tilePosition
                }
            }
        }

        while (currentDistance < stopDistance) {
            val left = homePosition.x - currentDistance
            val right = homePosition.x + currentDistance
            val bottom = homePosition.y - currentDistance
            val top = homePosition.y + currentDistance

            for (x in left..right) {
                for (y in bottom..top) {
                    var unitsInWay = false
                    game.allUnits.forEach { unit ->
                        if (unit.id != worker.id
                                && Math.abs(unit.position.x - x) < 4
                                && Math.abs(unit.position.y - y) < 4) {
                            unitsInWay = true
                        }
                    }

                    if (!unitsInWay)
                        return TilePosition(x, y)
                }
            }
            currentDistance += 2
        }
        return null
    }
}

class Builder {
    // A reference to the unit
    var unit: bwapi.Unit?

    // A reference to the building that is this builders home
    var home: bwapi.Unit?

    constructor(unit: bwapi.Unit,
                home: bwapi.Unit) {
        // Check the unit type is a worker
        if (!unit.isWorker())
            throw IllegalStateException("A builder cannot be created with unit type ${unit.type}")

        // Check the home is a building
        if (!unit.isBuilding())
            throw IllegalStateException("A builder cannot be created with home type ${home.type}")

        // Set the state
        this.unit = unit
        this.home = home
    }

    fun migrateToNewHome(newHome: bwapi.Unit) : Boolean {
        if (!newHome.isBuilding())
            throw IllegalStateException("A builder cannot migrate to a unit with type ${newHome.type}")
        return false
    }
}