// GOOSE SKIES - "Goose-first" native Android edition.
// Entire world simulation, geometry generation, 3D camera, renderer and HUD: Goose.
// JNI + Android GLSurfaceView are minimal platform-adapter code only.
// Compiled by official Goose v2026.3 -> C -> Android NDK ARM64.

import math;

// OpenGL ES 2 functions linked straight from Android's libGLESv2.so.
// Shader-source pointer adjustment is the only graphical C helper.
extern fn glEnable(cap: u32);
extern fn glDisable(cap: u32);
extern fn glDepthFunc(f: u32);
extern fn glClearColor(r: f32, g: f32, b: f32, a: f32);
extern fn glClear(mask: u32);
extern fn glViewport(x: i32, y: i32, w: i32, h: i32);
extern fn glCreateProgram() -> u32;
extern fn glAttachShader(program: u32, shader: u32);
extern fn glLinkProgram(program: u32);
extern fn glUseProgram(program: u32);
extern fn glGetAttribLocation(program: u32, name: const u8&) -> i32;
extern fn glGetUniformLocation(program: u32, name: const u8&) -> i32;
extern fn glEnableVertexAttribArray(location: u32);
extern fn glGenBuffers(n: i32, buffers: u32&);
extern fn glDeleteBuffers(n: i32, buffers: const u32&);
extern fn glBindBuffer(target: u32, buffer: u32);
extern fn glBufferData(target: u32, size: i64, data: const f32&, usage: u32);
extern fn glVertexAttribPointer(index: u32, size: i32, kind: u32, normalized: u8, stride: i32, offset: u64);
extern fn glUniformMatrix4fv(location: i32, count: i32, transpose: u8, value: const f32&);
extern fn glUniform3f(location: i32, a: f32, b: f32, c: f32);
extern fn glDrawArrays(mode: u32, start: i32, count: i32);
extern fn gs_compile_shader(kind: u32, source: const u8&) -> u32;

let ARRAY_BUFFER: u32 = 34962;
let STATIC_DRAW: u32 = 35044;
let DYNAMIC_DRAW: u32 = 35048;
let FLOAT: u32 = 5126;
let TRIANGLES: u32 = 4;
let DEPTH_TEST: u32 = 2929;
let COLOR_DEPTH: u32 = 16640;
let PI = 3.141592653589793;
let CHUNK_SIZE = 36.0;
let GRID_SIZE = 12;
let TILE_SIZE = 3.0;

// Goose engine state
var px: f64 = 0.0;
var py: f64 = 4.8;
var pz: f64 = 0.0;
var vx: f64 = 0.0;
var vy: f64 = 0.0;
var vz: f64 = 0.0;
var rings: i32 = 0;
var distance: f64 = 0.0;
var seed: i64 = 109717;
var ring_x: f64[3] = [12.0, -14.0, 26.0];
var ring_y: f64[3] = [5.0; 3];
var ring_z: f64[3] = [-15.0, -24.0, -7.0];
var seconds: f64 = 0.0;

// 5-finger touch state: platform forwards touch events but does no gameplay math
var joystick_pointer: i32 = -1;
var camera_pointer: i32 = -1;
var rise_pointer: i32 = -1;
var dive_pointer: i32 = -1;
var turbo_pointer: i32 = -1;
var stick_x: f64 = 0.0;
var stick_z: f64 = 0.0;
var yaw: f64 = 0.0;
var last_camera_x: f64 = 0.0;
var rising: bool = false;
var diving: bool = false;
var turbo: bool = false;
var width: i32 = 960;
var height: i32 = 540;

// One scratch region for static chunk meshing and small dynamic meshes.
// 9 interleaved floats: xyz, normal xyz, rgb.
var scratch: f32[120000] = [0.0; 120000];
var used: i32 = 0;
var chunk_buffer: u32[9] = [0; 9];
var chunk_cx: i32[9] = [-99999; 9];
var chunk_cz: i32[9] = [-99999; 9];
var chunk_count: i32[9] = [0; 9];
var current_cx: i32 = -99999;
var current_cz: i32 = -99999;
var moving_buffer: u32 = 0;
var shader_program: u32 = 0;
var attribute_position: i32 = -1;
var attribute_normal: i32 = -1;
var attribute_color: i32 = -1;
var uniform_matrix: i32 = -1;
var uniform_eye: i32 = -1;
var projection: f32[16] = [0.0; 16];
var view: f32[16] = [0.0; 16];
var combined: f32[16] = [0.0; 16];
var camera_x: f64 = 0.0;
var camera_y: f64 = 0.0;
var camera_z: f64 = 0.0;

// GLSL shader sources live in Goose; the GPU runs GLSL by design.
// Deliberately no Java or C geometry, game logic, or world renderer.
let SHADER_VERT = "attribute vec3 aPos;\nattribute vec3 aNormal;\nattribute vec3 aColor;\nuniform mat4 uVP;\nuniform vec3 uEye;\nvarying vec3 vCol;\nvarying float vFog;\nvoid main(){vec4 q=vec4(aPos,1.0);float light=max(dot(normalize(aNormal),normalize(vec3(-0.42,0.88,0.35))),0.0);vCol=aColor*(0.61+0.39*light);vFog=clamp((length(q.xyz-uEye)-38.0)/65.0,0.0,0.82);gl_Position=uVP*q;}\n\0";
let SHADER_FRAG = "precision mediump float;\nvarying vec3 vCol;\nvarying float vFog;\nvoid main(){gl_FragColor=vec4(mix(vCol,vec3(0.53,0.79,0.90),vFog),1.0);}\n\0";
let POS_NAME = "aPos\0";
let NORMAL_NAME = "aNormal\0";
let COLOR_NAME = "aColor\0";
let MATRIX_NAME = "uVP\0";
let EYE_NAME = "uEye\0";

export fn terrain_height(x: f64, z: f64) -> f64 {
    return -0.10 + sin(x * 0.075) * cos(z * 0.052) * 1.50
        + sin(x * 0.023 + z * 0.019) * 1.38
        + sin(z * 0.178 - x * 0.113) * 0.22;
}

fn rand_next(max_value: i64) -> i64 {
    seed = (seed * 48271) % 2147483647;
    return seed % max_value;
}

fn respawn_ring(i: i32) {
    let dx = (rand_next(81) - 40) as f64;
    let dz = (rand_next(81) - 40) as f64;
    var x = px + dx;
    var z = pz + dz;
    if x - px < 7.0 && x - px > -7.0 { x += 18.0; }
    if z - pz < 7.0 && z - pz > -7.0 { z -= 18.0; }
    ring_x[i] = x;
    ring_z[i] = z;
    ring_y[i] = terrain_height(x, z) + 2.5 + rand_next(5) as f64;
}

fn reset_world() {
    px = 0.0; pz = 0.0;
    py = terrain_height(0.0, 0.0) + 4.0;
    vx = 0.0; vy = 0.0; vz = 0.0;
    rings = 0; distance = 0.0;
    ring_x = [12.0, -14.0, 26.0];
    ring_z = [-15.0, -24.0, -7.0];
    var i: i32 = 0;
    while i < 3 {
        ring_y[i] = terrain_height(ring_x[i],ring_z[i]) + 3.2;
        i += 1;
    }
}

fn step(dt_raw: f64) {
    var dt = dt_raw;
    if dt < 0.0 { dt = 0.0; }
    if dt > 0.034 { dt = 0.034; }
    seconds += dt;
    let dx = stick_x * cos(yaw) + stick_z * sin(yaw);
    let dz = stick_x * sin(yaw) - stick_z * cos(yaw);
    let magnitude = sqrt(dx*dx + dz*dz);
    var move_x = dx;
    var move_z = dz;
    if magnitude > 1.0 { move_x /= magnitude; move_z /= magnitude; }
    var speed = 7.8;
    if turbo { speed = 14.0; }
    let smoothing = dt * 6.5;
    vx += (move_x * speed - vx) * smoothing;
    vz += (move_z * speed - vz) * smoothing;
    var up = 0.0;
    if rising { up += 1.0; }
    if diving { up -= 1.0; }
    vy += (up * 6.8 - vy) * smoothing;
    let old_x = px;
    let old_z = pz;
    px += vx * dt; pz += vz * dt; py += vy * dt;
    let ground = terrain_height(px,pz) + 1.45;
    if py < ground { py = ground; if vy < 0.0 { vy = 0.0; } }
    if py > 65.0 { py = 65.0; if vy > 0.0 { vy = 0.0; } }
    let delta_x = px - old_x;
    let delta_z = pz - old_z;
    distance += sqrt(delta_x*delta_x + delta_z*delta_z);
    var i: i32 = 0;
    while i < 3 {
        let x = px - ring_x[i];
        let y = py - ring_y[i];
        let z = pz - ring_z[i];
        if x*x + y*y + z*z < 3.2 {
            rings += 1;
            respawn_ring(i);
        }
        i += 1;
    }
}

export fn touch_event(action: i32, finger: i32, x: f64, y: f64, w: i32, h: i32) {
    width = w; height = h;
    let wf = w as f64; let hf = h as f64;
    let stick_radius = hf * 0.17;
    let stick_cx = wf * 0.15;
    let stick_cy = hf * 0.77;
    let rise_x = wf * 0.89;
    let dive_x = wf * 0.89;
    let turbo_x = wf * 0.75;
    let rise_y = hf * 0.60;
    let dive_y = hf * 0.84;
    let turbo_y = hf * 0.83;

    if action == 1 {
        if finger == joystick_pointer { joystick_pointer=-1; stick_x=0.0;stick_z=0.0; }
        if finger == camera_pointer { camera_pointer=-1; }
        if finger == rise_pointer { rise_pointer=-1;rising=false; }
        if finger == dive_pointer { dive_pointer=-1;diving=false; }
        if finger == turbo_pointer { turbo_pointer=-1;turbo=false; }
        return;
    }
    if action == 0 {
        if rise_pointer < 0 && abs(x-rise_x)<hf*0.10 && abs(y-rise_y)<hf*0.11 {
            rise_pointer=finger; rising=true;
        } else if dive_pointer<0 && abs(x-dive_x)<hf*0.10 && abs(y-dive_y)<hf*0.11 {
            dive_pointer=finger; diving=true;
        } else if turbo_pointer<0 && abs(x-turbo_x)<hf*0.10 && abs(y-turbo_y)<hf*0.12 {
            turbo_pointer=finger;turbo=true;
        } else if x<wf*0.43 && joystick_pointer<0 {
            joystick_pointer=finger;
        } else if camera_pointer<0 {
            camera_pointer=finger;
            last_camera_x=x;
        }
    }
    if finger==joystick_pointer {
        stick_x=(x-stick_cx)/stick_radius;
        stick_z=(stick_cy-y)/stick_radius;
        let length=sqrt(stick_x*stick_x+stick_z*stick_z);
        if length>1.0 {stick_x/=length;stick_z/=length;}
    }
    if finger==camera_pointer && action==2 {
        yaw+=(x-last_camera_x)*0.0048;
        last_camera_x=x;
    }
}

fn emit(x:f64,y:f64,z:f64,nx:f64,ny:f64,nz:f64,r:f64,g:f64,b:f64) {
    if used + 9 > 120000 { return; }
    scratch[used]=x as f32; used+=1;
    scratch[used]=y as f32; used+=1;
    scratch[used]=z as f32; used+=1;
    scratch[used]=nx as f32; used+=1;
    scratch[used]=ny as f32; used+=1;
    scratch[used]=nz as f32; used+=1;
    scratch[used]=r as f32; used+=1;
    scratch[used]=g as f32; used+=1;
    scratch[used]=b as f32; used+=1;
}

fn triangle(ax:f64,ay:f64,az:f64,bx:f64,by:f64,bz:f64,cx:f64,cy:f64,cz:f64,r:f64,g:f64,b:f64) {
    let ux=bx-ax;let uy=by-ay;let uz=bz-az;
    let vx=cx-ax;let vy=cy-ay;let vz=cz-az;
    var nx=uy*vz-uz*vy;
    var ny=uz*vx-ux*vz;
    var nz=ux*vy-uy*vx;
    let len=sqrt(nx*nx+ny*ny+nz*nz);
    if len<0.000001 { nx=0.0;ny=1.0;nz=0.0; } else {nx/=len;ny/=len;nz/=len;}
    emit(ax,ay,az,nx,ny,nz,r,g,b);
    emit(bx,by,bz,nx,ny,nz,r,g,b);
    emit(cx,cy,cz,nx,ny,nz,r,g,b);
}

fn quad(ax:f64,ay:f64,az,bx:f64,by:f64,bz,cx:f64,cy:f64,cz,dx:f64,dy:f64,dz,r:f64,g:f64,b:f64) {
    triangle(ax,ay,az,bx,by,bz,cx,cy,cz,r,g,b);
    triangle(ax,ay,az,cx,cy,cz,dx,dy,dz,r,g,b);
}

fn box(x:f64,y:f64,z:f64,sx:f64,sy:f64,sz:f64,r:f64,g:f64,b:f64) {
    let lx=x-sx*0.5;let hx=x+sx*0.5;
    let ly=y-sy*0.5;let hy=y+sy*0.5;
    let lz=z-sz*0.5;let hz=z+sz*0.5;
    quad(lx,ly,hz,hx,ly,hz,hx,hy,hz,lx,hy,hz,r,g,b);
    quad(hx,ly,lz,lx,ly,lz,lx,hy,lz,hx,hy,lz,r,g,b);
    quad(lx,ly,lz,lx,ly,hz,lx,hy,hz,lx,hy,lz,r,g,b);
    quad(hx,ly,hz,hx,ly,lz,hx,hy,lz,hx,hy,hz,r,g,b);
    quad(lx,hy,hz,hx,hy,hz,hx,hy,lz,lx,hy,lz,r,g,b);
    quad(lx,ly,lz,hx,ly,lz,hx,ly,hz,lx,ly,hz,r,g,b);
}

fn cone(x:f64,y:f64,z:f64,rad:f64,ht:f64,n:i32,r:f64,g:f64,b:f64) {
    var i:i32=0;
    while i<n {
        let a=(i as f64)*2.0*PI/(n as f64);
        let c=((i+1) as f64)*2.0*PI/(n as f64);
        triangle(x+cos(a)*rad,y,z+sin(a)*rad,
                 x+cos(c)*rad,y,z+sin(c)*rad,
                 x,y+ht,z,r,g,b);
        i+=1;
    }
}

fn sphere(x:f64,y:f64,z:f64,sx:f64,sy:f64,sz:f64,r:f64,g:f64,b:f64) {
    var j:i32=0;
    while j<6 {
        let lat0=-PI*0.5 + PI*(j as f64)/6.0;
        let lat1=-PI*0.5 + PI*((j+1) as f64)/6.0;
        var i:i32=0;
        while i<9 {
            let a=2.0*PI*(i as f64)/9.0;
            let c=2.0*PI*((i+1) as f64)/9.0;
            let ax=x+cos(lat0)*cos(a)*sx;
            let ay=y+sin(lat0)*sy;
            let az=z+cos(lat0)*sin(a)*sz;
            let bx=x+cos(lat0)*cos(c)*sx;
            let bz=z+cos(lat0)*sin(c)*sz;
            let cx=x+cos(lat1)*cos(c)*sx;
            let cy=y+sin(lat1)*sy;
            let cz=z+cos(lat1)*sin(c)*sz;
            let dx=x+cos(lat1)*cos(a)*sx;
            let dz=z+cos(lat1)*sin(a)*sz;
            triangle(ax,ay,az,bx,ay,bz,cx,cy,cz,r,g,b);
            triangle(ax,ay,az,cx,cy,cz,dx,cy,dz,r,g,b);
            i+=1;
        }
        j+=1;
    }
}

fn random_unit(x:f64,z:f64)->f64 {
    return sin(x*17.231+z*19.127)*cos(x*7.713-z*5.237);
}

fn build_chunk(cx:i32,cz:i32,idx:i32) {
    used=0;
    let start_x=(cx as f64)*CHUNK_SIZE;
    let start_z=(cz as f64)*CHUNK_SIZE;
    var iz:i32=0;
    while iz<GRID_SIZE {
        var ix:i32=0;
        while ix<GRID_SIZE {
            let x=start_x+(ix as f64)*TILE_SIZE;
            let z=start_z+(iz as f64)*TILE_SIZE;
            let h00=terrain_height(x,z);
            let h01=terrain_height(x,z+TILE_SIZE);
            let h10=terrain_height(x+TILE_SIZE,z);
            let h11=terrain_height(x+TILE_SIZE,z+TILE_SIZE);
            let avg=(h00+h01+h10+h11)*0.25;
            var r=0.32;var g=0.65;var b=0.35;
            if avg< -0.6 {r=0.58;g=0.72;b=0.64;}
            else if avg < 0.2 {r=0.85;g=0.73;b=0.47;}
            else if avg>2.15 {r=0.57;g=0.65;b=0.65;}
            let detail=random_unit(x,z)*0.043;
            triangle(x,h00,z,x,h01,z+TILE_SIZE,x+TILE_SIZE,h11,z+TILE_SIZE,
                     r+detail,g+detail,b+detail);
            triangle(x,h00,z,x+TILE_SIZE,h11,z+TILE_SIZE,x+TILE_SIZE,h10,z,
                     r+detail,g+detail,b+detail);
            ix+=1;
        }
        iz+=1;
    }
    var k:i32=0;
    while k<17 {
        let fx=(k as f64)*3.139+(cx as f64)*12.23+(cz as f64)*4.31;
        let fz=(k as f64)*7.239+(cz as f64)*17.31+(cx as f64)*9.71;
        let x=start_x+4.0+(sin(fx)*0.5+0.5)*(CHUNK_SIZE-8.0);
        let z=start_z+4.0+(cos(fz)*0.5+0.5)*(CHUNK_SIZE-8.0);
        let y=terrain_height(x,z);
        if y>0.1 && x*x+z*z>72.0 {
            let size=1.0+(random_unit(x*0.23,z*0.31)+1.0)*0.65;
            if k % 5 != 0 {
                box(x,y+size*0.65,z,size*0.26,size*1.3,size*0.26,0.42,0.30,0.20);
                cone(x,y+size*0.56,z,size*0.8,size*2.6,7,0.13,0.51,0.29);
                cone(x,y+size*1.55,z,size*0.61,size*1.85,7,0.15,0.62,0.36);
            } else {
                sphere(x,y+size*0.25,z,size*0.52,size*0.55,size*0.52,0.51,0.57,0.53);
            }
        }
        k+=1;
    }
    if (abs((cx*13+cz*7) % 11)==3 && !(cx==0 && cz==0) {
        let x=start_x+CHUNK_SIZE*0.52;
        let z=start_z+CHUNK_SIZE*0.48;
        let y=terrain_height(x,z);
        if y> -0.1 {
            box(x,y+1.3,z,4.2,2.6,4.0,0.79,0.60,0.39);
            cone(x,y+2.2,z,3.0,2.0,4,0.78,0.32,0.24);
            box(x,y+1.0,z-2.05,1.1,1.5,0.12,0.24,0.35,0.41);
        }
    }
    if cx==0 && cz==0 {
        let x=10.0;let z=10.0;
        let y=terrain_height(x,z);
        box(x,y+4.1,z,2.0,8.2,2.0,0.83,0.80,0.65);
        cone(x,y+8.1,z,2.3,2.8,8,0.85,0.33,0.23);
        box(x,y+6.5,z,2.6,1.1,2.6,0.25,0.37,0.46);
    }
    glBindBuffer(ARRAY_BUFFER,chunk_buffer[idx]);
    glBufferData(ARRAY_BUFFER,(used as i64)*4,scratch[0],STATIC_DRAW);
    chunk_count[idx]=used/9;
}

fn stream_chunks() {
    let cx=floor(px/CHUNK_SIZE) as i32;
    let cz=floor(pz/CHUNK_SIZE) as i32;
    if cx==current_cx && cz==current_cz { return; }
    current_cx=cx;current_cz=cz;
    var i:i32=0;
    while i<9 {
        let tx=cx + (i%3)-1;
        let tz=cz + (i/3)-1;
        if chunk_cx[i]!=tx || chunk_cz[i]!=tz {
            build_chunk(tx,tz,i);
            chunk_cx[i]=tx;
            chunk_cz[i]=tz;
        }
        i+=1;
    }
}

fn bind_attributes() {
    glVertexAttribPointer(attribute_position as u32,3,FLOAT,0,36,0);
    glVertexAttribPointer(attribute_normal as u32,3,FLOAT,0,36,12);
    glVertexAttribPointer(attribute_color as u32,3,FLOAT,0,36,24);
}

fn perspective() {
    var i:i32=0;while i<16 {projection[i]=0.0;i+=1;}
    var aspect=(width as f64)/(height as f64);
    if aspect<0.01 {aspect=1.0;}
    let f=1.8304877217;
    let near=0.15;let far=150.0;
    projection[0]=(f/aspect) as f32;
    projection[5]=f as f32;
    projection[10]=((far+near)/(near-far)) as f32;
    projection[11]=-1.0;
    projection[14]=((2.0*far*near)/(near-far)) as f32;
}

fn look_at(ex:f64,ey:f64,ez:f64,tx:f64,ty:f64,tz:f64) {
    var fx=tx-ex;var fy=ty-ey;var fz=tz-ez;
    let flen=sqrt(fx*fx+fy*fy+fz*fz);
    fx/=flen;fy/=flen;fz/=flen;
    // side = cross(forward, up=(0,1,0))
    var sx=-fz;var sy=0.0;var sz=fx;
    let slen=sqrt(sx*sx+sz*sz);
    sx/=slen;sz/=slen;
    let ux=sy*fz-sz*fy;
    let uy=sz*fx-sx*fz;
    let uz=sx*fy-sy*fx;
    view[0]=sx as f32;view[1]=ux as f32;view[2]=(-fx) as f32;view[3]=0.0;
    view[4]=sy as f32;view[5]=uy as f32;view[6]=(-fy) as f32;view[7]=0.0;
    view[8]=sz as f32;view[9]=uz as f32;view[10]=(-fz) as f32;view[11]=0.0;
    view[12]=(-sx*ex-sy*ey-sz*ez) as f32;
    view[13]=(-ux*ex-uy*ey-uz*ez) as f32;
    view[14]=(fx*ex+fy*ey+fz*ez) as f32;view[15]=1.0;
    var c:i32=0;
    while c<4 {
        var r:i32=0;
        while r<4 {
            var s:f32=0.0;
            var k:i32=0;
            while k<4 {
                s+=projection[k*4+r]*view[c*4+k];
                k+=1;
            }
            combined[c*4+r]=s;
            r+=1;
        }
        c+=1;
    }
}

fn dynamic_scene() {
    used=0;
    // A real goose model, built and animated inside Goose.
    let turn=sin(yaw);
    let forward=cos(yaw);
    let bob=sin(seconds*13.0)*0.16;
    sphere(px,py,pz,1.10,0.71,1.43,0.91,0.96,0.98);
    sphere(px,py+0.67,pz-forward*0.38,0.44,0.92,0.45,0.98,0.99,1.0);
    sphere(px,py+1.26,pz-forward*0.56,0.60,0.55,0.62,0.99,0.99,1.0);
    box(px,py+1.11,pz-forward*0.95,0.38,0.17,0.66,1.0,0.52,0.18);
    sphere(px-0.24,py+1.41,pz-forward*0.97,0.11,0.12,0.11,0.10,0.15,0.18);
    sphere(px+0.24,py+1.41,pz-forward*0.97,0.11,0.12,0.11,0.10,0.15,0.18);
    sphere(px-0.83,py+0.15+bob,pz,0.82,0.22,0.92,0.81,0.89,0.96);
    sphere(px+0.83,py+0.15-bob,pz,0.82,0.22,0.92,0.81,0.89,0.96);
    cone(px,py+0.05,pz+0.85,0.43,0.85,7,0.88,0.92,0.96);
    box(px-0.28,py-0.65,pz+0.08,0.16,0.42,0.20,0.99,0.55,0.21);
    box(px+0.28,py-0.65,pz+0.08,0.16,0.42,0.20,0.99,0.55,0.21);
    // Rings are modeled as 3D golden annuli and float as the player approaches.
    var n:i32=0;
    while n<3 {
        let x=ring_x[n];let y=ring_y[n]+sin(seconds*2.7+n as f64)*0.25;
        let z=ring_z[n];
        var i:i32=0;
        while i<16 {
            let a=2.0*PI*(i as f64)/16.0+seconds*0.50;
            let b=2.0*PI*((i+1) as f64)/16.0+seconds*0.50;
            let outer=1.10;let inner=0.78;
            quad(x+cos(a)*outer,y+sin(a)*outer,z,
                 x+cos(b)*outer,y+sin(b)*outer,z,
                 x+cos(b)*inner,y+sin(b)*inner,z,
                 x+cos(a)*inner,y+sin(a)*inner,z,1.0,0.76,0.14);
        i+=1;
        }
        sphere(x,y+1.5,z,0.28,0.28,0.28,1.0,0.94,0.44);
        n+=1;
    }
    // Floating clouds are volumetric 3D blobs.
    var cl:i32=0;
    while cl<6 {
        let grid=floor(px/28.0) as i32+cl-3;
        let x=(grid as f64)*28.0+sin(grid as f64)*7.0;
        let z=pz-43.0+cos((grid as f64)*3.19)*19.0;
        let y=17.0+sin((grid as f64)*1.97)*4.0;
        sphere(x,y,z,5.8,1.4,2.6,0.93,0.98,1.0);
        sphere(x+2.7,y+0.8,z,3.5,2.0,2.2,0.97,0.99,1.0);
        cl+=1;
    }
    // Shadow.
    let ground=terrain_height(px,pz)+0.10;
    sphere(px,ground,pz,1.6,0.06,1.2,0.21,0.41,0.28);
}

fn hud_disc(cx:f64,cy:f64,rad:f64,r:f64,g:f64,b:f64) {
    var i:i32=0;
    while i<20 {
        let a=2.0*PI*(i as f64)/20.0;
        let c=2.0*PI*((i+1) as f64)/20.0;
        triangle(cx,cy,0.0,cx+cos(a)*rad,cy+sin(a)*rad,0.0,
                 cx+cos(c)*rad,cy+sin(c)*rad,0.0,r,g,b);
        i+=1;
    }
}

fn hud_bar(x:f64,y:f64,w:f64,h:f64,r:f64,g:f64,b:f64) {
    quad(x,y,0.0,x+w,y,0.0,x+w,y+h,0.0,x,y+h,0.0,r,g,b);
}

fn draw_digit(d:i32,x:f64,y:f64) {
    var mask:i32=0;
    if d==0 { mask=63; }
    else if d==1 { mask=6; }
    else if d==2 {mask=91;}
    else if d==3 {mask=79;}
    else if d==4 {mask=102;}
    else if d==5 {mask=109;}
    else if d==6 {mask=125;}
    else if d==7 {mask=7;}
    else if d==8 {mask=127;}
    else if d==9 {mask=111;}
    let w=0.008;let len=0.045;let h=0.055;
    if mask & 1 != 0 {hud_bar(x-w,y+2.0*h,len+2.0*w,w,1.0,0.89,0.48);}
    if mask & 2 != 0 {hud_bar(x+len,y+h,w,h,1.0,0.89,0.48);}
    if mask & 4 != 0 {hud_bar(x+len,y,w,h,1.0,0.89,0.48);}
    if mask & 8 != 0 {hud_bar(x-w,y,len+2.0*w,w,1.0,0.89,0.48);}
    if mask & 16 != 0 {hud_bar(x-w,y,w,h,1.0,0.89,0.48);}
    if mask & 32 != 0 {hud_bar(x-w,y+h,w,h,1.0,0.89,0.48);}
    if mask & 64 != 0 {hud_bar(x-w,y+h,len+2.0*w,w,1.0,0.89,0.48);}
}

fn draw_hud() {
    // HUD geometry also comes from Goose, not Android canvas.
    used=0;
    let cx=-0.69;
    let cy=-0.70;
    hud_disc(cx,cy,0.19,0.13,0.36,0.50);
    hud_disc(cx+stick_x*0.11,cy+stick_z*0.11,0.077,0.74,0.93,0.97);
    hud_disc(0.81,-0.34,0.112,0.17,0.72,0.53);
    hud_disc(0.81,-0.73,0.112,0.21,0.57,0.81);
    hud_disc(0.50,-0.72,0.118,0.96,0.64,0.21);
    // Indicators show when a finger presses the corresponding button.
    if rising { hud_disc(0.81,-0.34,0.055,0.84,1.0,0.88); }
    if diving { hud_disc(0.81,-0.73,0.055,0.82,0.92,1.0); }
    if turbo { hud_disc(0.50,-0.72,0.055,1.0,0.96,0.77); }
    // A 3D-game HUD score, using pure Goose 7-segment numbers.
    let ones=rings%10;
    let tens=(rings/10)%10;
    let hundreds=(rings/100)%10;
    draw_digit(hundreds,0.63,0.75);
    draw_digit(tens,0.72,0.75);
    draw_digit(ones,0.81,0.75);
    hud_disc(0.49,0.82,0.036,1.0,0.81,0.23);
}

export fn surface_created() {
    glClearColor(0.48,0.78,0.91,1.0);
    glEnable(DEPTH_TEST);
    glDepthFunc(515);
    let vs=gs_compile_shader(35633,SHADER_VERT[0]);
    let fs=gs_compile_shader(35632,SHADER_FRAG[0]);
    shader_program=glCreateProgram();
    glAttachShader(shader_program,vs);
    glAttachShader(shader_program,fs);
    glLinkProgram(shader_program);
    glUseProgram(shader_program);
    attribute_position=glGetAttribLocation(shader_program,POS_NAME[0]);
    attribute_normal=glGetAttribLocation(shader_program,NORMAL_NAME[0]);
    attribute_color=glGetAttribLocation(shader_program,COLOR_NAME[0]);
    uniform_matrix=glGetUniformLocation(shader_program,MATRIX_NAME[0]);
    uniform_eye=glGetUniformLocation(shader_program,EYE_NAME[0]);
    glEnableVertexAttribArray(attribute_position as u32);
    glEnableVertexAttribArray(attribute_normal as u32);
    glEnableVertexAttribArray(attribute_color as u32);
    var i:i32=0;
    while i<9 {
        glGenBuffers(1,chunk_buffer[i]);
        chunk_cx[i]=-99999;chunk_cz[i]=-99999;
        i+=1;
    }
    glGenBuffers(1,moving_buffer);
    current_cx=-99999;current_cz=-99999;
    perspective();
}

export fn surface_resized(w:i32,h:i32) {
    width=w;height=h;
    if height<1 {height=1;}
    glViewport(0,0,width,height);
    perspective();
}

export fn render_frame(dt:f64) {
    step(dt);
    camera_x=px-sin(yaw)*9.5;
    camera_y=py+5.7;
    camera_z=pz+cos(yaw)*9.5;
    let tx=px+sin(yaw)*4.0;
    let tz=pz-cos(yaw)*4.0;
    look_at(camera_x,camera_y,camera_z,tx,py+1.3,tz);
    glClear(COLOR_DEPTH);
    glUseProgram(shader_program);
    glUniformMatrix4fv(uniform_matrix,1,0,combined[0]);
    glUniform3f(uniform_eye,camera_x as f32,camera_y as f32,camera_z as f32);
    stream_chunks();
    var i:i32=0;
    while i<9 {
        glBindBuffer(ARRAY_BUFFER,chunk_buffer[i]);
        bind_attributes();
        glDrawArrays(TRIANGLES,0,chunk_count[i]);
        i+=1;
    }
    dynamic_scene();
    glBindBuffer(ARRAY_BUFFER,moving_buffer);
    glBufferData(ARRAY_BUFFER,(used as i64)*4,scratch[0],DYNAMIC_DRAW);
    bind_attributes();
    glDrawArrays(TRIANGLES,0,used/9);
    // Render the 2D overlay with an identity projection. Fog is zero here.
    glDisable(DEPTH_TEST);
    var identity:f32[16]=[0.0;16];
    identity[0]=1.0;identity[5]=1.0;identity[10]=1.0;identity[15]=1.0;
    glUniformMatrix4fv(uniform_matrix,1,0,identity[0]);
    glUniform3f(uniform_eye,0.0,0.0,0.0);
    draw_hud();
    glBufferData(ARRAY_BUFFER,(used as i64)*4,scratch[0],DYNAMIC_DRAW);
    bind_attributes();
    glDrawArrays(TRIANGLES,0,used/9);
    glEnable(DEPTH_TEST);
}

export fn get_score() -> i32 {return rings;}
export fn get_distance() -> f64 {return distance;}
fn main(){ reset_world(); }
