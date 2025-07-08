
package com.example.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

public class BorderManager {

    private WorldBorder border;
    private double[] borderSizes = {2500, 2000, 1500, 1000, 500, 100, 10, 0};

    public BorderManager() {
        World world = Bukkit.getWorlds().get(0);
        this.border = world.getWorldBorder();
    }

    public void setBorder(int phase) {
        if (phase >= 0 && phase < borderSizes.length) {
            double size = borderSizes[phase];
            border.setSize(size);
        }
    }

    public void shrinkBorder(int phase, long time) {
        if (phase >= 0 && phase < borderSizes.length) {
            double size = borderSizes[phase];
            border.setSize(size, time);
        }
    }

    public WorldBorder getBorder() {
        return border;
    }
}
