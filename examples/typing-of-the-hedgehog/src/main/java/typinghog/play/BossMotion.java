package typinghog.play;

import com.openggf.game.GameServices;

/** ROM motion vocabulary for the puppets. Typing holds at telegraphs/windows, never native AI. */
final class BossMotion {
    enum Kind { GREEN_HILL, FINAL_ZONE, DEATH_EGG, EMERALD_HILL, SHIP }
    enum Attack { BALL, CYLINDERS, PLASMA, WALK, SENSOR_SLAM, BOMBS, PUNCH, DRILL }

    final Kind kind;
    Attack attack;
    int cycle;
    int x;
    int y;
    int homeX;
    int homeY;
    int side;
    int tick;
    int ballAngle = 0x4080;
    int ballSpeed = -0x200;
    boolean ballReverse;
    int cylinderTravel;
    int occupied;
    int partner;
    int punchX;
    int punchY;
    int sensorX;
    int sensorY;
    int flightY;
    int velocityY;
    int walkX;
    int walkY;
    int bombLandRow;
    int chargeEndX;
    boolean impact;

    BossMotion(Kind kind) { this.kind = kind; }

    void anchor(Course course, int side) {
        var camera = GameServices.camera();
        this.side = side;
        homeX = camera.getX() + (side > 0 ? camera.getWidth() * 3 / 4 : camera.getWidth() / 4);
        homeY = switch (kind) {
            // GBall_AnchorPos=32 and GBall_PosData ball radius=$60: bottom of swing at Sonic.
            case GREEN_HILL -> Math.max(camera.getY() + 64, course.anchorY() - 128);
            // ObjC7_ChildDeltas: head -$34, feet +$3C. Keep the head below the HUD.
            case DEATH_EGG -> Math.max(camera.getY() + 120, course.anchorY() - 73);
            case EMERALD_HILL -> course.anchorY() - 17;
            case FINAL_ZONE -> camera.getY() + 112;
            case SHIP -> Math.max(camera.getY() + 72, course.anchorY() - 72);
        };
        x = homeX;
        y = homeY;
    }

    void begin(Course course) {
        tick = 0;
        impact = false;
        bombLandRow = 0;
        cylinderTravel = 0;
        flightY = 0;
        velocityY = 0;
        attack = switch (kind) {
            case GREEN_HILL, SHIP -> Attack.BALL;
            case FINAL_ZONE -> (cycle & 1) == 0 ? Attack.CYLINDERS : Attack.PLASMA;
            // loc_3D640/byte_3D680 increments first, then selects 0,2,4,2 (walk, jump,
            // punch, jump). loc_3D7F0 creates bombs after a landing when facing the player.
            case DEATH_EGG -> switch (cycle % 6) {
                case 0 -> Attack.WALK;
                case 1, 4 -> Attack.SENSOR_SLAM;
                case 2, 5 -> Attack.BOMBS;
                default -> Attack.PUNCH;
            };
            case EMERALD_HILL -> Attack.DRILL;
        };
        if (attack == Attack.CYLINDERS) {
            // BossFinal_CylinderPairs: 0,1; 1,2; 2,3; 3,0; either member can hold Eggman.
            int first = course.random().nextInt(4);
            boolean swap = course.random().nextBoolean();
            occupied = swap ? (first + 1) % 4 : first;
            partner = swap ? first : (first + 1) % 4;
        }
        if (attack == Attack.BALL) {
            // BGHZ_ChgDir: $100 (1px/frame), with the $80-frame crossing from ShipMove.
            x = Math.max(GameServices.camera().getX() + 64,
                    Math.min(GameServices.camera().getX() + GameServices.camera().getWidth() - 64,
                            course.anchorX() + ((cycle & 1) == 0 ? side * 64 : 0)));
            // Keep GBall_Move's accumulator between attacks: alternate half-swings form
            // the native 256-row period. Only the typing hold pauses it.
        } else {
            x = homeX;
        }
        walkX = x << 8;
        walkY = homeY << 8;
        y = homeY;
        sensorX = course.anchorX();
        sensorY = course.anchorY();
        punchX = x - 12 * side;
        punchY = y + 8;
        // loc_2F484 reverses the drill car at arena bounds. The controlled stage's
        // turn is relative to Sonic, instead of the stock world addresses.
        // Typing-stage spacing: the car turns before its body reaches the stand. The
        // loc_2F098 drill remains 54px ahead; its hazard reaches across the remaining gap.
        chargeEndX = course.anchorX() + side * 80;
    }

    /** Advance just the ROM motion, after the player has had time to type. */
    boolean advance(Course course) {
        tick++;
        switch (attack) {
            case BALL -> {
                // BGHZ_ShipMove/ChgDir: wait $40 rows, then a $40-row crossing at $100.
                // Alternate the 64px passes so the craft stays over the standing arena.
                if (tick > 64) x += (cycle % 2 == 0 ? -side : side);
                // BGHZ_ShipUpdate: CalcSine >> 6, counter +=2 (4px bob, 128-row period).
                y = homeY + (int) Math.round(Math.sin(tick * Math.PI / 64) * 4);
                swing();
                if (Math.abs(ballX() - course.anchorX()) < 28
                        && Math.abs(ballY() - course.anchorY()) < 40) impact = true;
                return tick >= 128;
            }
            case CYLINDERS -> {
                // EggmanCylinder_Top/Bottom: .5px until $10, then 3px, total travel $A0.
                cylinderTravel = Math.min(160 << 8,
                        cylinderTravel + (cylinderTravel > 16 << 8 ? 0x300 : 0x80));
                x = pillarX(occupied);
                y = pillarY(occupied) + (occupied < 2 ? -10 : 14);
                if (cylinderTravel >= 160 << 8) { impact = true; return true; }
            }
            case PLASMA -> {
                // BossPlasma_Drop/Move: $140 vertical speed (1.25px/frame).
                flightY += 0x140;
                if (plasmaY() >= course.anchorY()) { impact = true; return true; }
            }
            case WALK -> {
                // One stride from off_3E40C -> byte_3E450/468/480/494.
                // Headers $20,$10,$10,$10; body deltas (0,-4),(-16,-4),(-8,4),(0,8)
                // are signed sixteenths per row (loc_3E1AA shifts by 4 into 8.8 velocity).
                int dx = tick <= 32 ? 0 : tick <= 48 ? -16 : tick <= 64 ? -8 : 0;
                int dy = tick <= 48 ? -4 : tick <= 64 ? 4 : 8;
                walkX += dx * 16 * side;
                walkY += dy * 16;
                x = walkX >> 8;
                y = walkY >> 8;
                if (tick == 80) impact = true;
                return tick >= 80;
            }
            case PUNCH -> {
                // loc_3DACC: arm drops for $10 rows at +$20 gravity; loc_3DB32 flies
                // for $20 rows at $800 X speed, then returns for another $20.
                if (tick <= 16) {
                    velocityY += 0x20;
                    flightY += velocityY;
                    punchY = homeY + 8 + (flightY >> 8);
                } else if (tick <= 48) {
                    punchX -= 8 * side;
                    if (!impact && (punchX - course.anchorX()) * side <= 0) impact = true;
                } else {
                    punchX += 8 * side;
                    punchY += Integer.signum(homeY + 8 - punchY);
                }
                if (tick == 48) impact = true;
                return tick >= 80;
            }
            case SENSOR_SLAM -> {
                // loc_3D720/744 rises $100px at -$200 for $80 rows; loc_3D784/7B8
                // relocates to the sensor lock then drops at $800 for $20 rows.
                if (tick <= 128) y = homeY - tick * 2;
                else {
                    // The sensor/hazard locks onto Sonic, while the body lands on its own
                    // side of the typing stage. Keep loc_3D784/7B8's rise/drop velocities.
                    x = homeX;
                    y = homeY - 256 + (tick - 128) * 8;
                }
                if (tick >= 160) { impact = true; return true; }
            }
            case BOMBS -> {
                // ObjC7_EggmanBomb loc_3DF04 -> ObjectMoveAndFall (+$38 gravity),
                // then loc_3DF36 waits $40 before exploding. Land at the controlled stand's floor.
                if (bombLandRow == 0) {
                    velocityY += 0x38;
                    flightY += velocityY;
                    if (homeY - 20 + (flightY >> 8) >= sensorY) bombLandRow = tick;
                }
                if (bombLandRow > 0 && tick - bombLandRow >= 64) { impact = true; return true; }
            }
            case DRILL -> {
                // Obj56_VehicleMain_Sub2_2 / Sub4: x_vel=-$200, reversing at arena edge.
                x -= side * 2;
                if (!impact && (x - 54 * side - course.anchorX()) * side <= 28) impact = true;
                return (x - chargeEndX) * side <= 0;
            }
        }
        return false;
    }

    int bombRows() {
        int falling = 0, speed = 0, rows = 0;
        while (homeY - 20 + (falling >> 8) < sensorY) {
            speed += 0x38;
            falling += speed;
            rows++;
        }
        return rows + 64;
    }

    int chargeRows() { return Math.max(1, (Math.abs(homeX - chargeEndX) + 1) / 2); }

    int plasmaX(int copy) {
        int start = GameServices.camera().getX() + GameServices.camera().getWidth() * (copy + 1) / 5;
        // BossPlasma_Drop sets obVelX to the pixel difference (an 8.8 velocity), not *$100.
        return start + (sensorX - start) * tick / 256;
    }

    private void swing() {
        // GBall_Move uses fixed-point angle and +/-8 acceleration, switches at +/-$200.
        // This produces a 256-row period; there is no invented sine amplitude/period.
        ballSpeed += ballReverse ? -8 : 8;
        ballAngle = (ballAngle + ballSpeed) & 0xffff;
        if (ballSpeed == 0x200) ballReverse = true;
        if (ballSpeed == -0x200) ballReverse = false;
    }

    double ballRadians() { return (ballAngle >> 8) * Math.PI / 128; }
    int ballX() { return x + (int) Math.round(Math.cos(ballRadians()) * 96) * side; }
    int ballY() { return y + 32 + (int) Math.round(Math.sin(ballRadians()) * 96); }
    int plasmaY() { return GameServices.camera().getY() + 72 + (flightY >> 8); }
    int pillarX(int i) {
        // EggmanCylinder_PosData floor at $80/$100, ceiling at $40/$C0;
        // extend horizontal spacing with viewport width, preserving that stagger/order.
        int lane = switch (i) { case 0 -> 2; case 1 -> 4; case 2 -> 1; default -> 3; };
        return GameServices.camera().getX() + GameServices.camera().getWidth() * lane / 5;
    }
    int pillarY(int i) {
        int travel = cylinderTravel >> 8;
        return GameServices.camera().getY() + (i < 2 ? 272 - travel : -80 + travel);
    }
    int pillarFrame(int i) {
        int travel = cylinderTravel >> 8;
        int start = i < 2 ? 8 : 39;
        return travel < start ? 0 : Math.min(10, 1 + (travel - start) / 16);
    }
}
