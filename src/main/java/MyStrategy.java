import model.*;

import java.util.ArrayList;
import java.util.List;

public class MyStrategy {
    private final List<Entity> resourceList = new ArrayList<>();
    private Vec2Int rootPos = null;
    private int rootSize = 1;
    private int resources = 0;
    private int currentUnits = 0;
    private int maxUnits = 0;
    private int maxBuilderUnits = 0;
    private int maxRangeUnits = 0;
    private int maxMeleeUnits = 0;
    private int builderUnits = 0;
    private int meleeUnits = 0;
    private int rangeUnits = 0;

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        Action result = new Action(new java.util.HashMap<>());
        int myId = playerView.getMyId();
        fillUnitsData(playerView, myId);
        System.out.println("-------------------------");
        System.out.println("RESOURCES: " + resources);
        System.out.println("UNITS: " + currentUnits + " / " + maxUnits);
        System.out.println("  builder: " + builderUnits + " / " + maxBuilderUnits);
        System.out.println("  melee:   " + meleeUnits + " / " + maxMeleeUnits);
        System.out.println("  range:   " + rangeUnits + " / " + maxRangeUnits);
        System.out.println("ROOT POSITION: " + rootPos.getX() + ":" + rootPos.getY());
        System.out.println("ROOT SIZE: " + rootSize);

        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId()==null || entity.getPlayerId()!=myId) {
                continue;
            }

            EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());

            MoveAction moveAction = null;
            BuildAction buildAction = null;

            EntityType entityType = entity.getEntityType();
            EntityType[] attackEntityTypes = new EntityType[0];
            switch (entityType) {
                case MELEE_UNIT -> {
                    moveAction = new MoveAction(
                            new Vec2Int(playerView.getMapSize() - 1, playerView.getMapSize() - 1),
                            true,
                            true);
                }
                case RANGED_UNIT -> {
                    moveAction = new MoveAction(
                            new Vec2Int(playerView.getMapSize() - 1, playerView.getMapSize() - 1),
                            true,
                            true);
                }
                case BUILDER_UNIT -> {
                    attackEntityTypes = new EntityType[] {EntityType.RESOURCE};
                    moveAction = findNearResource(entity);
                }
                case BUILDER_BASE -> {
                    buildAction = createUnitNearBase(playerView, entity, EntityType.BUILDER_UNIT);
                }
                case RANGED_BASE -> {
                    buildAction = createUnitNearBase(playerView, entity, EntityType.RANGED_UNIT);
                }
                case MELEE_BASE -> {
                    buildAction = createUnitNearBase(playerView, entity, EntityType.MELEE_UNIT);
                }
                default -> {
                    /* ignore */
                }
            }

            if (buildAction != null) {
                System.out.println("build");
            }

            result.getEntityActions().put(entity.getId(), new EntityAction(
                    moveAction,
                    buildAction,
                    new AttackAction(
                            null, new AutoAttack(properties.getSightRange(), attackEntityTypes)
                    ),
                    null
            ));
        }
        return result;
    }

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        debugInterface.getState();
    }

    private void fillUnitsData(PlayerView playerView, int playerId) {
        for (Player player : playerView.getPlayers()) {
            if (player.getId() == playerId) {
                resources = player.getResource();
            }
        }

        currentUnits = 0;
        maxUnits = 0;
        maxBuilderUnits = 1;
        maxMeleeUnits = 1;
        maxRangeUnits = 1;
        builderUnits = 0;
        meleeUnits = 0;
        rangeUnits = 0;
        resourceList.clear();
        for (Entity entity : playerView.getEntities()) {
            if (entity.getEntityType() == EntityType.RESOURCE) {
                resourceList.add(entity);
            }
            if (entity.getPlayerId() == null || entity.getPlayerId() != playerId) {
                continue;
            }
            System.out.println(entity.getEntityType());
            EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());
            maxUnits += properties.getPopulationProvide();
            switch (entity.getEntityType()) {
                case MELEE_UNIT -> meleeUnits++;
                case BUILDER_UNIT -> builderUnits++;
                case RANGED_UNIT -> rangeUnits++;
                case BUILDER_BASE -> {
                    EntityProperties buildBaseProp = playerView.getEntityProperties().get(entity.getEntityType());
                    maxBuilderUnits += buildBaseProp.getPopulationProvide();
                    if (rootPos == null) {
                        rootPos = entity.getPosition();
                        rootSize = buildBaseProp.getSize();
                    }
                }
                case RANGED_BASE -> {
                    EntityProperties buildBaseProp = playerView.getEntityProperties().get(entity.getEntityType());
                    maxRangeUnits += buildBaseProp.getPopulationProvide();
                }
                case MELEE_BASE -> {
                    EntityProperties buildBaseProp = playerView.getEntityProperties().get(entity.getEntityType());
                    maxMeleeUnits += buildBaseProp.getPopulationProvide();
                }
            }
        }
        currentUnits = meleeUnits + builderUnits + rangeUnits;
    }

    private MoveAction findNearResource(Entity builder) {
        MoveAction mv = null;
        int distance = 0;
        Vec2Int nearRes = null;
        for (Entity entity : resourceList) {
            if (nearRes == null) {
                nearRes = entity.getPosition();
                distance = lineDistance(rootPos, entity.getPosition());
            }
            int delta = lineDistance(rootPos, entity.getPosition());
            if (delta < distance) {
                distance = delta;
                nearRes = entity.getPosition();
            }
        }
        if (nearRes != null) {
            mv = new MoveAction(nearRes,true,true);
        }
        return mv;
    }

    private BuildAction createUnitNearBase(PlayerView playerView, Entity buildEntity, EntityType unitEntityType) {
        EntityProperties baseProp = playerView.getEntityProperties().get(buildEntity.getEntityType());
        EntityProperties unitProp = playerView.getEntityProperties().get(unitEntityType);
        BuildAction buildAction = null;
        boolean canBuild = true;
        switch (unitEntityType) {
            case BUILDER_UNIT -> canBuild = builderUnits <= maxBuilderUnits;
            case MELEE_UNIT -> canBuild = meleeUnits <= maxMeleeUnits;
            case RANGED_UNIT -> canBuild = rangeUnits <= maxRangeUnits;
        }
        if (canBuild && resources>=unitProp.getInitialCost()) {
            System.out.println(">> create unit " + unitEntityType);
            buildAction = new BuildAction(
                    unitEntityType,
                    new Vec2Int(
                            buildEntity.getPosition().getX() + baseProp.getSize(),
                            buildEntity.getPosition().getY() + baseProp.getSize() - 1
                    )
            );
        }
        return buildAction;
    }

    private int lineDistance(Vec2Int a, Vec2Int b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }
}