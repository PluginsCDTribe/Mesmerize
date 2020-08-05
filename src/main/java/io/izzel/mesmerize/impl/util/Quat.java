package io.izzel.mesmerize.impl.util;

import io.izzel.mesmerize.api.data.NumberValue;
import org.bukkit.util.Vector;

public class Quat {

    private final double x;
    private final double y;
    private final double z;
    private final double w;

    public Quat(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return x * x + y * y + z * z + w * w;
    }

    public Vector rotate(Vector vector) {
        return rotate(vector.getX(), vector.getY(), vector.getZ());
    }

    public Vector rotate(double x, double y, double z) {
        final double length = length();
        if (Math.abs(length) < NumberValue.DBL_EPSILON) {
            throw new ArithmeticException("Cannot rotate by zero");
        }
        final double nx = this.x / length;
        final double ny = this.y / length;
        final double nz = this.z / length;
        final double nw = this.w / length;
        final double px = nw * x + ny * z - nz * y;
        final double py = nw * y + nz * x - nx * z;
        final double pz = nw * z + nx * y - ny * x;
        final double pw = -nx * x - ny * y - nz * z;
        return new Vector(
            pw * -nx + px * nw - py * nz + pz * ny,
            pw * -ny + py * nw - pz * nx + px * nz,
            pw * -nz + pz * nw - px * ny + py * nx);
    }

    public static Quat fromAngleRadAxis(double angle, Vector vector) {
        return fromAngleRadAxis(angle, vector.getX(), vector.getY(), vector.getZ());
    }

    public static Quat fromAngleRadAxis(double angle, double x, double y, double z) {
        final double halfAngle = angle / 2;
        final double q = Math.sin(halfAngle) / Math.sqrt(x * x + y * y + z * z);
        return new Quat(x * q, y * q, z * q, Math.cos(halfAngle));
    }
}
