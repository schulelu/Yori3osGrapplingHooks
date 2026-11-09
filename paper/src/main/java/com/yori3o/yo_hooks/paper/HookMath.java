package com.yori3o.yo_hooks.paper;

/**
 * The mod's rope, climb and jump formulas, free of Paper types so they can be tested on their own.
 * Sources: common/mixin/PlayerMixin (rope), network/ServerReceiver (climb), event/ClientEvents (jump impulse),
 * item/HookItem (range and cast speed).
 */
public final class HookMath {

    /** Shortest rope; the mod clamps every length change to this. */
    public static final double MIN_LENGTH = 0.4;

    private HookMath() {
    }

    public record V(double x, double y, double z) {
        public static final V ZERO = new V(0, 0, 0);

        public V add(V o) {
            return new V(x + o.x, y + o.y, z + o.z);
        }

        public V sub(V o) {
            return new V(x - o.x, y - o.y, z - o.z);
        }

        public V mul(double f) {
            return new V(x * f, y * f, z * f);
        }

        public double dot(V o) {
            return x * o.x + y * o.y + z * o.z;
        }

        public double length() {
            return Math.sqrt(dot(this));
        }

        public V normalize() {
            double l = length();
            return l < 1e-9 ? ZERO : mul(1 / l);
        }
    }

    /** Velocity after the rope step; {@code taut} when the player was beyond the rope length. */
    public record Rope(V velocity, boolean taut, double distance, V towardHook) {
    }

    /**
     * One tick of the mod's rope physics (PlayerMixin, head of Player.travel): split the velocity into the part
     * toward the hook and the rest, then hold the player on a rigid rope (or a spring with softHook).
     * Positive radial speed means toward the hook. Vanilla gravity and drag follow on the client.
     */
    public static Rope ropeStep(V eye, V hook, V velocity, double length, boolean climbingUp, boolean softHook,
                                double stiffness, double climbStep, boolean onGround, boolean fallFlying) {
        V d = hook.sub(eye);
        double dist = d.length();
        if (dist < 1e-6) {
            return new Rope(velocity, false, dist, V.ZERO);
        }
        V u = d.mul(1 / dist);
        double radial = velocity.dot(u);
        V tangential = velocity.sub(u.mul(radial));
        double mult = 1.01;
        boolean taut = dist > length;
        if (taut) {
            double stretch = dist - length;
            mult = 1.047;
            if (climbingUp && !softHook && length > MIN_LENGTH) {
                mult = 1.017;
                radial = climbStep;
            }
            radial = softHook ? Math.min(radial + stretch * stiffness, 1) : Math.max(radial, stretch * 0.055);
        }
        if (!onGround && !fallFlying) {
            tangential = tangential.mul(mult);
            radial *= 0.99;
        }
        return new Rope(tangential.add(u.mul(radial)), taut, dist, u);
    }

    /**
     * The client's delta movement at the head of its next tick, estimated from the movement the server saw:
     * vanilla air movement is followed by gravity 0.08 and drag (0.98 vertical, 0.91 horizontal); on the
     * ground, block friction 0.6 makes the horizontal factor 0.546.
     */
    public static V headVelocity(V moved, boolean onGround) {
        if (onGround) {
            return new V(moved.x() * 0.546, 0, moved.z() * 0.546);
        }
        return new V(moved.x() * 0.91, (moved.y() - 0.08) * 0.98, moved.z() * 0.91);
    }

    /** The mod's merge of the current speed with the jump push along one axis. */
    public static double target(double current, double push) {
        if (current * push >= 0) {
            return push >= 0 ? Math.max(current, push) : Math.min(current, push);
        }
        return push * 0.3;
    }

    /** Jumping off the rope: a push along the full 3D look vector and a minimum upward speed. */
    public static V jumpImpulse(V velocity, V look, int agility, double jumpMultiplier) {
        double forward = (0.47 + 0.09 * agility) * jumpMultiplier;
        double up = (0.41 + 0.04 * agility) * jumpMultiplier;
        double px = look.x() * forward;
        double pz = look.z() * forward;
        return new V(target((velocity.x() + px) / 2, px), Math.max(velocity.y(), up), target((velocity.z() + pz) / 2, pz));
    }

    /** The jump boost only works while the hook is no more than about 8.6 degrees below horizontal. */
    public static boolean jumpAllowed(V eye, V hook, boolean always) {
        return always || hook.sub(eye).normalize().y() > -0.15;
    }

    public static int range(int tierRange, int longReach, boolean funnyMode) {
        return funnyMode ? 79 : tierRange + (int) (longReach * 3.5);
    }

    public static double castSpeed(boolean funnyMode, int agility) {
        return (funnyMode ? 4.0 : 2.0) + agility * 0.25;
    }

    public static double climbStep(double climbSpeed, int agility, boolean funnyMode) {
        return (climbSpeed + agility * 0.041) * (funnyMode ? 1.5 : 1.0);
    }

    public static double climbUp(double length, double step) {
        return Math.max(MIN_LENGTH, length - step);
    }

    public static double climbDown(double length, double climbSpeed, int maxRange) {
        return Math.min(maxRange - 2, length + climbSpeed * 1.5);
    }

    public static double castExhaustion(double decreaseSatiety) {
        return decreaseSatiety / 1.5;
    }

    public static double climbExhaustion(double decreaseSatiety, int agility) {
        return decreaseSatiety / 75 + agility * 0.0065;
    }

    public static double jumpExhaustion(double decreaseSatiety) {
        return decreaseSatiety / 3;
    }
}
