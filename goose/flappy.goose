// FLAPPY GOOSE - logica original escrita 100% em Goose.
// As funcoes exportadas sao chamadas pelo Android via ponte JNI.
// Mundo logico: 360 x 640; chao na coordenada y = 584.

var mode: i32 = 0;           // 0 = pronto, 1 = jogando, 2 = fim
var score: i32 = 0;
var best: i32 = 0;
var seed: i64 = 17012026;

var bird_y: f64 = 300.0;
var bird_vy: f64 = 0.0;

var pipe0_x: f64 = 430.0;
var pipe1_x: f64 = 650.0;
var pipe2_x: f64 = 870.0;
var pipe0_gap: f64 = 270.0;
var pipe1_gap: f64 = 375.0;
var pipe2_gap: f64 = 225.0;
var pipe0_passed: bool = false;
var pipe1_passed: bool = false;
var pipe2_passed: bool = false;

fn next_gap() -> f64 {
    seed = (seed * 48271) % 2147483647;
    return 175.0 + (seed % 250) as f64;
}

export fn reset_game(new_seed: i32) {
    if new_seed > 0 {
        seed = new_seed;
    }
    mode = 0;
    score = 0;
    bird_y = 300.0;
    bird_vy = 0.0;
    pipe0_x = 430.0;
    pipe1_x = 650.0;
    pipe2_x = 870.0;
    pipe0_gap = next_gap();
    pipe1_gap = next_gap();
    pipe2_gap = next_gap();
    pipe0_passed = false;
    pipe1_passed = false;
    pipe2_passed = false;
}

export fn flap() {
    if mode == 2 {
        reset_game(0);
    }
    mode = 1;
    bird_vy = -290.0;
}

fn hit_pipe(x: f64, gap: f64) -> bool {
    // Hitbox do passaro: x 77..103; y = bird_y +/- 13.
    if 103.0 > x && 77.0 < x + 64.0 {
        if bird_y - 13.0 < gap - 84.0 || bird_y + 13.0 > gap + 84.0 {
            return true;
        }
    }
    return false;
}

fn die() {
    mode = 2;
    if score > best {
        best = score;
    }
}

export fn step_game(raw_dt: f64) {
    if mode != 1 {
        return;
    }

    // Evita saltos de simulacao quando o app e retomado.
    var dt = raw_dt;
    if dt < 0.0 { dt = 0.0; }
    if dt > 0.035 { dt = 0.035; }

    bird_vy += 790.0 * dt;
    bird_y += bird_vy * dt;

    pipe0_x -= 154.0 * dt;
    pipe1_x -= 154.0 * dt;
    pipe2_x -= 154.0 * dt;

    if !pipe0_passed && pipe0_x + 64.0 < 77.0 {
        score += 1;
        pipe0_passed = true;
    }
    if !pipe1_passed && pipe1_x + 64.0 < 77.0 {
        score += 1;
        pipe1_passed = true;
    }
    if !pipe2_passed && pipe2_x + 64.0 < 77.0 {
        score += 1;
        pipe2_passed = true;
    }

    // Recicla os 3 canos: nao precisa alocar memoria.
    if pipe0_x < -70.0 {
        var far = pipe1_x;
        if pipe2_x > far { far = pipe2_x; }
        pipe0_x = far + 220.0;
        pipe0_gap = next_gap();
        pipe0_passed = false;
    }
    if pipe1_x < -70.0 {
        var far = pipe0_x;
        if pipe2_x > far { far = pipe2_x; }
        pipe1_x = far + 220.0;
        pipe1_gap = next_gap();
        pipe1_passed = false;
    }
    if pipe2_x < -70.0 {
        var far = pipe0_x;
        if pipe1_x > far { far = pipe1_x; }
        pipe2_x = far + 220.0;
        pipe2_gap = next_gap();
        pipe2_passed = false;
    }

    if bird_y < 15.0 || bird_y > 569.0 {
        die();
        return;
    }

    if hit_pipe(pipe0_x, pipe0_gap) ||
       hit_pipe(pipe1_x, pipe1_gap) ||
       hit_pipe(pipe2_x, pipe2_gap) {
        die();
    }
}

export fn get_mode() -> i32 { return mode; }
export fn get_score() -> i32 { return score; }
export fn get_best() -> i32 { return best; }
export fn get_bird_y() -> f64 { return bird_y; }
export fn get_bird_velocity() -> f64 { return bird_vy; }

export fn get_pipe_x(index: i32) -> f64 {
    if index == 0 { return pipe0_x; }
    if index == 1 { return pipe1_x; }
    return pipe2_x;
}

export fn get_pipe_gap(index: i32) -> f64 {
    if index == 0 { return pipe0_gap; }
    if index == 1 { return pipe1_gap; }
    return pipe2_gap;
}

fn main() {
    reset_game(17012026);
}