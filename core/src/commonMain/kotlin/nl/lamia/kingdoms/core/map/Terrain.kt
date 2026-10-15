package nl.lamia.kingdoms.core.map

enum class Resource { Grain, Wool, Timber, Ore, Peat, Fish }

/** Province terrain (Design §3.3). [yield] is armies grown per round, before the coastal bonus. */
enum class Terrain(val resource: Resource, val yield: Int) {
    Plains(Resource.Grain, 2),
    Hills(Resource.Wool, 1),
    Forest(Resource.Timber, 1),
    Mountains(Resource.Ore, 1),
    Marsh(Resource.Peat, 1),
}

/** Extra yield for a province that borders the sea, which also gives it [Resource.Fish] (Design §3.3). */
const val COASTAL_YIELD = 1

/** Border features (Design §3.4); [defence] multiplies the defender's strength across the border. */
enum class Feature(val defence: Double) {
    River(1.15),
    Ridge(1.20),
    Marsh(1.25),
    Lake(1.30),
    MountainRange(1.50),
}
