package me.taubsie.carrylogs.application.enums;

import me.taubsie.dungeonhub.common.config.Nameable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum CarryPrice implements Nameable {
    F4_COMP(CarryType.F4, CarryTier.COMPLETION, 500000L),
    F4_S(CarryType.F4, CarryTier.S, 700000L),
    F5_COMP(CarryType.F5, CarryTier.COMPLETION, 400000L),
    F5_S(CarryType.F5, CarryTier.S, 600000L),
    F5_SP(CarryType.F5, CarryTier.S_PLUS, 750000L),
    F6_COMP(CarryType.F6, CarryTier.COMPLETION, 650000L),
    F6_S(CarryType.F6, CarryTier.S, 1000000L),
    F6_SP(CarryType.F6, CarryTier.S_PLUS, 1400000L),
    F7_COMP(CarryType.F7, CarryTier.COMPLETION, 5000000L),
    F7_S(CarryType.F7, CarryTier.S, 8000000L),
    F7_SP(CarryType.F7, CarryTier.S_PLUS, 11000000L),
    MM_1(CarryType.MASTER_MODE, CarryTier.FLOOR_1, 1000000L),
    MM_2(CarryType.MASTER_MODE, CarryTier.FLOOR_2, 2000000L),
    MM_3(CarryType.MASTER_MODE, CarryTier.FLOOR_3, 3000000L),
    MM_4(CarryType.MASTER_MODE, CarryTier.FLOOR_4, 10000000L),
    MM_5(CarryType.MASTER_MODE, CarryTier.FLOOR_5, 4000000L),
    MM_6(CarryType.MASTER_MODE, CarryTier.FLOOR_6, 6000000L),
    MM_7(CarryType.MASTER_MODE, CarryTier.FLOOR_7, 24000000L),
    EMAN_T3(CarryType.EMAN, CarryTier.TIER_3, 800000L),
    EMAN_T4(CarryType.EMAN, CarryTier.TIER_4, 2500000L, 2000000L, 10),
    BLAZE_T2(CarryType.BLAZE, CarryTier.TIER_2, 1000000L, 850000L, 10),
    BLAZE_T3(CarryType.BLAZE, CarryTier.TIER_3, 2000000L, 1500000L, 10),
    BLAZE_T4(CarryType.BLAZE, CarryTier.TIER_4, 5000000L, 4000000L, 10),
    KUUDRA_BASIC(CarryType.KUUDRA, CarryTier.BASIC, 7000000L),
    KUUDRA_HOT(CarryType.KUUDRA, CarryTier.HOT, 11000000L),
    KUUDRA_BURNING(CarryType.KUUDRA, CarryTier.BURNING, 18000000L),
    KUUDRA_FIERY(CarryType.KUUDRA, CarryTier.FIERY, 23000000L),
    KUUDRA_INFERNAL(CarryType.KUUDRA, CarryTier.INFERNAL, 50000000L);

    private final CarryType carryType;
    private final CarryTier carryTier;
    private final long price;
    private long bulkPrice = 0L;
    private int bulkAmount = 0;

    CarryPrice(CarryType carryType, CarryTier carryTier, long price) {
        this.carryType = carryType;
        this.carryTier = carryTier;
        this.price = price;
    }

    CarryPrice(CarryType carryType, CarryTier carryTier, long price, long bulkPrice, int bulkAmount) {
        this.carryType = carryType;
        this.carryTier = carryTier;
        this.price = price;
        this.bulkPrice = bulkPrice;
        this.bulkAmount = bulkAmount;
    }

    public static long calculatePrice(CarryType carryType, CarryTier carryTier, long amount) {
        if(carryTier == null) {
            return 0L;
        }

        Optional<CarryPrice> carryPrice = Arrays.stream(CarryPrice.values())
                .filter(carryPrice1 -> carryPrice1.getCarryType() == carryType && carryPrice1.getCarryTier() == carryTier)
                .findFirst();

        if(carryPrice.isEmpty()) {
            return 0L;
        }

        if(carryPrice.get().getBulkAmount() > 0 && carryPrice.get().getBulkAmount() <= amount) {
            return carryPrice.get().getBulkPrice() * amount;
        }

        return carryPrice.get().getPrice() * amount;
    }

    @Override
    public String getName() {
        return name();
    }

    public long getBulkPrice() {
        return bulkPrice;
    }

    public int getBulkAmount() {
        return bulkAmount;
    }

    public long getPrice() {
        return price;
    }

    public CarryType getCarryType() {
        return carryType;
    }

    public CarryTier getCarryTier() {
        return carryTier;
    }

    public static List<CarryPrice> getDungeonPrices() {
        CarryType[] dungeonTypes = new CarryType[] {CarryType.F4, CarryType.F5, CarryType.F6, CarryType.F7, CarryType.MASTER_MODE};

        return Arrays.stream(CarryPrice.values()).filter(carryPrice -> Arrays.stream(dungeonTypes).anyMatch(type -> type.equals(carryPrice.getCarryType()))).toList();
    }

    public static List<CarryPrice> getEndermanPrices() {
        CarryType[] slayerTypes = new CarryType[] {CarryType.EMAN};

        return Arrays.stream(CarryPrice.values()).filter(carryPrice -> Arrays.stream(slayerTypes).anyMatch(type -> type.equals(carryPrice.getCarryType()))).toList();
    }

    public static List<CarryPrice> getBlazePrices() {
        CarryType[] slayerTypes = new CarryType[] {CarryType.BLAZE};

        return Arrays.stream(CarryPrice.values()).filter(carryPrice -> Arrays.stream(slayerTypes).anyMatch(type -> type.equals(carryPrice.getCarryType()))).toList();
    }

    public static List<CarryPrice> getKuudraPrices() {
        CarryType[] kuudraTypes = new CarryType[] {CarryType.KUUDRA};

        return Arrays.stream(CarryPrice.values()).filter(carryPrice -> Arrays.stream(kuudraTypes).anyMatch(type -> type.equals(carryPrice.getCarryType()))).toList();
    }
}