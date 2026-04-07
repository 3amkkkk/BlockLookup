package nl.amk.blocklookup.db.model;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.util.Objects;

public record BlockSnapshot(String materialKey, String blockData) {

    public static BlockSnapshot from(BlockState state) {
        Material material = state.getType();
        String data = state.getBlockData().getAsString();
        return new BlockSnapshot(material.getKey().asString(), data);
    }

    public static BlockSnapshot air() {
        return new BlockSnapshot(Material.AIR.getKey().asString(), "");
    }

    public boolean matches(Material currentType, String currentData) {
        if (!Objects.equals(materialKey, currentType.getKey().asString())) return false;
        if (blockData == null || blockData.isEmpty()) return true;
        return Objects.equals(blockData, currentData);
    }
}

