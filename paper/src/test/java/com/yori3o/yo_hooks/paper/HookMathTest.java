package com.yori3o.yo_hooks.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yori3o.yo_hooks.paper.HookMath.V;
import org.junit.jupiter.api.Test;

/** The port must reproduce the mod's formulas; expected values are worked out from the mod's source. */
class HookMathTest {

    private static final double EPS = 1e-9;
    private static final V EYE = new V(0, 0, 0);
    private static final V HOOK_ABOVE = new V(0, 10, 0);

    @Test
    void slackRopeOnlyDampsInTheAir() {
        V vel = new V(0.2, -0.3, 0);
        HookMath.Rope r = HookMath.ropeStep(EYE, HOOK_ABOVE, vel, 12, false, false, 0.1, 0.115, false, false);
        assertFalse(r.taut());
        // radial (toward the hook, +y) -0.3 * 0.99, tangential 0.2 * 1.01
        assertEquals(0.2 * 1.01, r.velocity().x(), EPS);
        assertEquals(-0.3 * 0.99, r.velocity().y(), EPS);
    }

    @Test
    void tautRigidRopeStopsFallingAway() {
        V vel = new V(0.1, -0.5, 0); // falling away from a hook 10 blocks above, rope 8 long: 2 blocks stretched
        HookMath.Rope r = HookMath.ropeStep(EYE, HOOK_ABOVE, vel, 8, false, false, 0.1, 0.115, false, false);
        assertTrue(r.taut());
        assertEquals(Math.max(-0.5, 2 * 0.055) * 0.99, r.velocity().y(), EPS);
        assertEquals(0.1 * 1.047, r.velocity().x(), EPS);
    }

    @Test
    void softHookIsASpringCappedAtOne() {
        V vel = new V(0, -0.5, 0);
        HookMath.Rope r = HookMath.ropeStep(EYE, HOOK_ABOVE, vel, 5, false, true, 0.3, 0.115, true, false);
        assertEquals(Math.min(-0.5 + 5 * 0.3, 1), r.velocity().y(), EPS); // on the ground: no damping factors
    }

    @Test
    void climbingPullsAtClimbSpeed() {
        V vel = new V(0.3, 0, 0);
        double step = HookMath.climbStep(0.115, 1, false);
        HookMath.Rope r = HookMath.ropeStep(EYE, HOOK_ABOVE, vel, 9, true, false, 0.1, step, false, false);
        assertEquals(Math.max(step, 0.055) * 0.99, r.velocity().y(), EPS);
        assertEquals(0.3 * 1.017, r.velocity().x(), EPS);
    }

    @Test
    void elytraKeepsTheClampButNotTheMultipliers() {
        V vel = new V(1, -1, 0);
        HookMath.Rope r = HookMath.ropeStep(EYE, HOOK_ABOVE, vel, 8, false, false, 0.1, 0.115, false, true);
        assertEquals(1, r.velocity().x(), EPS);
        assertEquals(0.11, r.velocity().y(), EPS);
    }

    @Test
    void targetMergesLikeTheMod() {
        assertEquals(0.5, HookMath.target(0.3, 0.5), EPS);
        assertEquals(0.7, HookMath.target(0.7, 0.5), EPS);
        assertEquals(-0.6, HookMath.target(-0.6, -0.2), EPS);
        assertEquals(0.5 * 0.3, HookMath.target(-0.4, 0.5), EPS);
    }

    @Test
    void jumpImpulse() {
        V look = new V(1, 0, 0);
        V out = HookMath.jumpImpulse(new V(0.1, -0.2, 0), look, 0, 1);
        assertEquals(HookMath.target((0.1 + 0.47) / 2, 0.47), out.x(), EPS);
        assertEquals(0.41, out.y(), EPS);
        V agile = HookMath.jumpImpulse(V.ZERO, look, 2, 1.4);
        assertEquals((0.47 + 0.18) * 1.4, agile.x(), EPS);
        assertEquals((0.41 + 0.08) * 1.4, agile.y(), EPS);
    }

    @Test
    void jumpOnlyWhenTheHookIsNotFarBelow() {
        assertTrue(HookMath.jumpAllowed(EYE, new V(10, -1, 0), false)); // about -5.7 degrees
        assertFalse(HookMath.jumpAllowed(EYE, new V(10, -2, 0), false)); // about -11 degrees
        assertTrue(HookMath.jumpAllowed(EYE, new V(0, -10, 0), true));
    }

    @Test
    void rangeClimbAndCosts() {
        assertEquals(12, HookMath.range(12, 0, false));
        assertEquals(12 + 14, HookMath.range(12, 4, false)); // Long Reach IV: (int) 14.0
        assertEquals(12 + 7, HookMath.range(12, 2, false));
        assertEquals(79, HookMath.range(12, 4, true));
        assertEquals(2.25, HookMath.castSpeed(false, 1), EPS);
        assertEquals(0.115, HookMath.climbStep(0.115, 0, false), EPS);
        assertEquals(HookMath.MIN_LENGTH, HookMath.climbUp(0.45, 0.115), EPS);
        assertEquals(10, HookMath.climbDown(9.9, 0.115, 12), EPS); // capped at range - 2
        assertEquals(1.1 / 1.5, HookMath.castExhaustion(1.1), EPS);
        assertEquals(1.1 / 75 + 0.013, HookMath.climbExhaustion(1.1, 2), EPS);
    }

    @Test
    void hookFliesFreelyInsideTheRope() {
        V pos = new V(3, 1, 0);
        V vel = new V(2, -0.1, 0);
        HookMath.Catch c = HookMath.catchStep(pos, vel, V.ZERO, 9.5);
        assertFalse(c.taut());
        assertEquals(5, c.position().x(), EPS);
        assertEquals(0.9, c.position().y(), EPS);
        assertEquals(vel, c.velocity());
    }

    @Test
    void ropeCatchesTheHookAtItsEnd() {
        // Thrown straight out along +x: the step past the end lands exactly on the sphere and stops dead
        HookMath.Catch c = HookMath.catchStep(new V(8.5, 0, 0), new V(2, 0, 0), V.ZERO, 9.5);
        assertTrue(c.taut());
        assertEquals(9.5, c.position().length(), EPS);
        assertEquals(9.5, c.position().x(), EPS);
        assertEquals(0, c.velocity().length(), EPS);
    }

    @Test
    void caughtHookKeepsItsSwing() {
        // On the sphere, moving down and outward: the outward part goes, the downward swing stays
        V pos = new V(9.5, 0, 0);
        HookMath.Catch c = HookMath.catchStep(pos, new V(0.3, -0.5, 0.2), V.ZERO, 9.5);
        assertTrue(c.taut());
        assertEquals(9.5, c.position().length(), EPS);
        V u = c.position().normalize();
        assertEquals(0, c.velocity().dot(u), EPS); // no outward speed left
        // u = (9.8, -0.5, 0.2) / |..|; the tangential part of (0.3, -0.5, 0.2) is nearly all of y and z
        assertEquals(-0.4832, c.velocity().y(), 1e-3);
        assertEquals(0.1933, c.velocity().z(), 1e-3);
    }

    @Test
    void ropeDoesNotPullAnInwardHook() {
        // Falling back toward the player after a steep throw: the slack rope changes nothing
        V vel = new V(-0.2, -0.6, 0);
        HookMath.Catch c = HookMath.catchStep(new V(2, 8, 0), vel, V.ZERO, 9.5);
        assertFalse(c.taut());
        assertEquals(vel, c.velocity());
    }

    @Test
    void headVelocityFromObservedMovement() {
        V air = HookMath.headVelocity(new V(1, 0.5, -1), false);
        assertEquals(0.91, air.x(), EPS);
        assertEquals((0.5 - 0.08) * 0.98, air.y(), EPS);
        assertEquals(0, HookMath.headVelocity(new V(1, 0.5, 1), true).y(), EPS);
    }
}
