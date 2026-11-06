// GOOSE SKIES: open-world game simulation written in native Goose.
// Android OpenGL ES owns only presentation and touch input.
// Goose controls flying, terrain collision, RNG, collectible spawning and progress.
import math;

var px: f64 = 0.0;
var py: f64 = 4.2;
var pz: f64 = 0.0;
var vx: f64 = 0.0;
var vy: f64 = 0.0;
var vz: f64 = 0.0;
var traveled: f64 = 0.0;
var feathers: i32 = 0;
var seed: i64 = 207717;

var r0x: f64 = 12.0;
var r0z: f64 = -15.0;
var r0y: f64 = 3.0;
var r1x: f64 = -13.0;
var r1z: f64 = -24.0;
var r1y: f64 = 3.0;
var r2x: f64 = 26.0;
var r2z: f64 = -7.0;
var r2y: f64 = 3.0;

// Smooth infinite landscape. The Java terrain renderer uses the same equation.
export fn terrain_height(x: f64, z: f64) -> f64 {
    return -0.10
        + sin(x * 0.075) * cos(z * 0.052) * 1.50
        + sin(x * 0.023 + z * 0.019) * 1.38
        + sin(z * 0.178 - x * 0.113) * 0.22;
}

fn randint(max_value: i64) -> i64 {
    seed = (seed * 48271) % 2147483647;
    return seed % max_value;
}

fn relocate_ring(index: i32) {
    var rx: f64 = px + (randint(81) - 40) as f64;
    var rz: f64 = pz + (randint(81) - 40) as f64;
    // Never spawn a collectible directly inside the player.
    if rx - px < 8.0 && rx - px > -8.0 { rx += 18.0; }
    if rz - pz < 8.0 && rz - pz > -8.0 { rz -= 18.0; }
    let ry = terrain_height(rx, rz) + 2.6 + (randint(4) as f64);
    if index == 0 { r0x = rx; r0y = ry; r0z = rz; }
    if index == 1 { r1x = rx; r1y = ry; r1z = rz; }
    if index == 2 { r2x = rx; r2y = ry; r2z = rz; }
}

export fn reset_world(new_seed: i32) {
    if new_seed > 0 { seed = new_seed as i64; }
    px = 0.0;
    pz = 0.0;
    py = terrain_height(0.0, 0.0) + 4.0;
    vx = 0.0;
    vy = 0.0;
    vz = 0.0;
    traveled = 0.0;
    feathers = 0;
    r0x = 12.0; r0z = -15.0;
    r1x = -13.0; r1z = -24.0;
    r2x = 26.0; r2z = -7.0;
    r0y = terrain_height(r0x, r0z) + 3.0;
    r1y = terrain_height(r1x, r1z) + 3.0;
    r2y = terrain_height(r2x, r2z) + 3.0;
}

fn ring_hit(rx: f64, ry: f64, rz: f64) -> bool {
    let dx = px - rx;
    let dy = py - ry;
    let dz = pz - rz;
    return dx * dx + dy * dy + dz * dz < 3.10;
}

// axes are already rotated by the Android camera (the physical movement stays native).
// axis_y = +1 rise / -1 dive. boost = 1 for faster flying.
export fn step_world(raw_dt: f64, axis_x: f64, axis_z: f64, axis_y: f64, boost: i32) {
    var dt = raw_dt;
    if dt < 0.0 { dt = 0.0; }
    if dt > 0.034 { dt = 0.034; }

    let old_x = px;
    let old_z = pz;
    var ix = axis_x;
    var iz = axis_z;
    let input_len = sqrt(ix * ix + iz * iz);
    if input_len > 1.0 {
        ix /= input_len;
        iz /= input_len;
    }

    var speed = 7.5;
    if boost > 0 { speed = 13.5; }
    let response = dt * 6.5;
    vx += (ix * speed - vx) * response;
    vz += (iz * speed - vz) * response;
    let requested_vy = axis_y * 6.7;
    vy += (requested_vy - vy) * dt * 6.5;

    px += vx * dt;
    pz += vz * dt;
    py += vy * dt;

    let floor = terrain_height(px, pz) + 1.35;
    if py < floor {
        py = floor;
        if vy < 0.0 { vy = 0.0; }
    }
    if py > 64.0 {
        py = 64.0;
        if vy > 0.0 { vy = 0.0; }
    }

    let dx = px - old_x;
    let dz = pz - old_z;
    traveled += sqrt(dx * dx + dz * dz);

    if ring_hit(r0x, r0y, r0z) {
        feathers += 1;
        relocate_ring(0);
    }
    if ring_hit(r1x, r1y, r1z) {
        feathers += 1;
        relocate_ring(1);
    }
    if ring_hit(r2x, r2y, r2z) {
        feathers += 1;
        relocate_ring(2);
    }
}

export fn player_x() -> f64 { return px; }
export fn player_y() -> f64 { return py; }
export fn player_z() -> f64 { return pz; }
export fn player_vx() -> f64 { return vx; }
export fn player_vy() -> f64 { return vy; }
export fn player_vz() -> f64 { return vz; }
export fn score() -> i32 { return feathers; }
export fn distance() -> f64 { return traveled; }

export fn ring_x(index: i32) -> f64 {
    if index == 0 { return r0x; }
    if index == 1 { return r1x; }
    return r2x;
}
export fn ring_y(index: i32) -> f64 {
    if index == 0 { return r0y; }
    if index == 1 { return r1y; }
    return r2y;
}
export fn ring_z(index: i32) -> f64 {
    if index == 0 { return r0z; }
    if index == 1 { return r1z; }
    return r2z;
}

fn main() {
    reset_world(17012026);
}
