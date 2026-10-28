"use client";

import { Canvas, ThreeEvent, useFrame, useThree } from "@react-three/fiber";
import { OrbitControls, RoundedBox } from "@react-three/drei";
import { Eye, EyeOff, Maximize, RotateCcw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import * as THREE from "three";
import type { OrbitControls as OrbitControlsImpl } from "three-stdlib";

export type SpatialFeatureId =
  | "bed"
  | "window"
  | "bathroom"
  | "workspace"
  | "storage"
  | "entrance";

type SpatialFeature = {
  id: SpatialFeatureId;
  label: string;
  description: string;
  position: [number, number, number];
  camera: [number, number, number];
};

type SpatialRoomViewerProps = {
  roomName: string;
  hasBalcony: boolean;
};

type CameraRigProps = {
  focus: SpatialFeature | null;
  controlsRef: React.RefObject<OrbitControlsImpl | null>;
};

const DEFAULT_CAMERA: [number, number, number] = [4.9, 2.65, 5.35];
const DEFAULT_TARGET: [number, number, number] = [0, 1.05, -0.45];

function CameraRig({ focus, controlsRef }: CameraRigProps) {
  const { camera } = useThree();
  const desiredPosition = useRef(new THREE.Vector3(...DEFAULT_CAMERA));
  const desiredTarget = useRef(new THREE.Vector3(...DEFAULT_TARGET));

  useEffect(() => {
    desiredPosition.current.set(...(focus?.camera ?? DEFAULT_CAMERA));
    desiredTarget.current.set(...(focus?.position ?? DEFAULT_TARGET));
  }, [focus]);

  useFrame(() => {
    camera.position.lerp(desiredPosition.current, 0.065);

    const controls = controlsRef.current;
    if (!controls) {
      return;
    }

    controls.target.lerp(desiredTarget.current, 0.08);
    controls.update();
  });

  return null;
}

function Hotspot({
  feature,
  active,
  onSelect,
}: {
  feature: SpatialFeature;
  active: boolean;
  onSelect: () => void;
}) {
  const handleClick = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation();
    onSelect();
  };

  return (
    <group position={feature.position}>
      <mesh onClick={handleClick} scale={active ? 1.3 : 1} castShadow>
        <sphereGeometry args={[0.105, 24, 24]} />
        <meshStandardMaterial
          color={active ? "#d8e9ff" : "#ffffff"}
          emissive={active ? "#b97818" : "#2f6655"}
          emissiveIntensity={active ? 1.4 : 0.75}
          roughness={0.28}
        />
      </mesh>
      <pointLight
        color={active ? "#d8e9ff" : "#d8fff0"}
        intensity={active ? 1.2 : 0.38}
        distance={1.35}
      />
    </group>
  );
}

function BedArea() {
  return (
    <group position={[-0.7, 0, -1.25]}>
      <RoundedBox args={[3.55, 0.42, 2.3]} radius={0.12} position={[0, 0.34, 0]} castShadow>
        <meshStandardMaterial color="#806c58" roughness={0.82} />
      </RoundedBox>
      <RoundedBox args={[3.35, 0.3, 2.12]} radius={0.16} position={[0, 0.7, 0.02]} castShadow>
        <meshStandardMaterial color="#f5f0e7" roughness={0.92} />
      </RoundedBox>
      <RoundedBox args={[3.65, 1.35, 0.18]} radius={0.08} position={[0, 1.35, -1.1]} castShadow>
        <meshStandardMaterial color="#29483e" roughness={0.7} />
      </RoundedBox>
      <RoundedBox args={[1.35, 0.18, 0.7]} radius={0.12} position={[-0.82, 1.0, -0.63]} rotation={[-0.12, 0, 0]}>
        <meshStandardMaterial color="#e7d6b9" />
      </RoundedBox>
      <RoundedBox args={[1.35, 0.18, 0.7]} radius={0.12} position={[0.82, 1.0, -0.63]} rotation={[-0.12, 0, 0]}>
        <meshStandardMaterial color="#f2e9d9" />
      </RoundedBox>
      <mesh position={[0, 0.92, 0.45]} castShadow>
        <boxGeometry args={[3.05, 0.06, 0.78]} />
        <meshStandardMaterial color="#b78b58" roughness={0.85} />
      </mesh>
    </group>
  );
}

function Workspace() {
  return (
    <group position={[2.55, 0, -1.85]}>
      <RoundedBox args={[1.75, 0.12, 0.72]} radius={0.05} position={[0, 0.83, 0]} castShadow>
        <meshStandardMaterial color="#6b4f39" roughness={0.65} />
      </RoundedBox>
      {[-0.7, 0.7].map((x) => (
        <mesh key={x} position={[x, 0.4, 0]} castShadow>
          <boxGeometry args={[0.08, 0.8, 0.08]} />
          <meshStandardMaterial color="#28241f" />
        </mesh>
      ))}
      <RoundedBox args={[0.7, 0.72, 0.7]} radius={0.12} position={[0, 0.4, 0.9]} castShadow>
        <meshStandardMaterial color="#355347" roughness={0.75} />
      </RoundedBox>
      <mesh position={[0.25, 1.08, -0.05]}>
        <boxGeometry args={[0.75, 0.48, 0.04]} />
        <meshStandardMaterial color="#17201d" />
      </mesh>
    </group>
  );
}

function BathroomArea() {
  return (
    <group position={[-3.05, 0, 1.65]}>
      <mesh position={[0, 1.3, -0.55]} receiveShadow>
        <boxGeometry args={[1.95, 2.65, 0.1]} />
        <meshStandardMaterial color="#d8d1c6" roughness={0.92} />
      </mesh>
      <RoundedBox args={[1.4, 0.16, 0.72]} radius={0.06} position={[0, 0.62, -0.25]} castShadow>
        <meshStandardMaterial color="#f4f2ec" />
      </RoundedBox>
      <mesh position={[0, 1.45, -0.47]}>
        <boxGeometry args={[1.2, 1.05, 0.03]} />
        <meshPhysicalMaterial color="#cfe0dd" metalness={0.2} roughness={0.12} />
      </mesh>
      <mesh position={[0.42, 0.84, -0.16]}>
        <cylinderGeometry args={[0.04, 0.04, 0.42, 18]} />
        <meshStandardMaterial color="#b9a36f" metalness={0.65} roughness={0.25} />
      </mesh>
    </group>
  );
}

function RoomShell({ hasBalcony }: { hasBalcony: boolean }) {
  return (
    <group>
      <mesh position={[0, -0.08, 0]} receiveShadow>
        <boxGeometry args={[8.5, 0.16, 6.6]} />
        <meshStandardMaterial color="#bda77f" roughness={0.88} />
      </mesh>
      <mesh position={[0, 1.75, -3.2]} receiveShadow>
        <boxGeometry args={[8.5, 3.65, 0.14]} />
        <meshStandardMaterial color="#eee7da" roughness={0.95} />
      </mesh>
      <mesh position={[-4.18, 1.75, 0]} receiveShadow>
        <boxGeometry args={[0.14, 3.65, 6.55]} />
        <meshStandardMaterial color="#e8e0d4" />
      </mesh>
      <mesh position={[4.18, 1.75, -1.55]} receiveShadow>
        <boxGeometry args={[0.14, 3.65, 3.25]} />
        <meshStandardMaterial color="#e8e0d4" />
      </mesh>
      <mesh position={[0, 3.52, 0]} receiveShadow>
        <boxGeometry args={[8.5, 0.1, 6.55]} />
        <meshStandardMaterial color="#f4efe6" />
      </mesh>

      <mesh position={[1.85, 1.65, 3.17]}>
        <boxGeometry args={[4.35, 2.75, 0.07]} />
        <meshPhysicalMaterial
          color="#9dc8cf"
          transmission={0.45}
          transparent
          opacity={0.52}
          roughness={0.08}
        />
      </mesh>
      <mesh position={[1.85, 2.95, 3.12]}>
        <boxGeometry args={[4.55, 0.12, 0.12]} />
        <meshStandardMaterial color="#263f38" />
      </mesh>
      <mesh position={[-0.3, 1.65, 3.12]}>
        <boxGeometry args={[0.1, 2.75, 0.1]} />
        <meshStandardMaterial color="#263f38" />
      </mesh>

      {hasBalcony && (
        <group position={[1.85, 0, 4.05]}>
          <mesh position={[0, -0.02, 0]} receiveShadow>
            <boxGeometry args={[4.55, 0.12, 1.55]} />
            <meshStandardMaterial color="#8f7557" roughness={0.8} />
          </mesh>
          <mesh position={[0, 0.78, 0.72]}>
            <boxGeometry args={[4.55, 0.07, 0.07]} />
            <meshStandardMaterial color="#263f38" />
          </mesh>
        </group>
      )}
    </group>
  );
}

function PremiumDemoRoom({
  features,
  activeFeature,
  onFeatureSelect,
  hasBalcony,
}: {
  features: SpatialFeature[];
  activeFeature: SpatialFeatureId | null;
  onFeatureSelect: (id: SpatialFeatureId) => void;
  hasBalcony: boolean;
}) {
  return (
    <group>
      <RoomShell hasBalcony={hasBalcony} />
      <BedArea />
      <Workspace />
      <BathroomArea />

      <RoundedBox args={[1.35, 2.15, 0.68]} radius={0.06} position={[-3.35, 1.08, -2.65]} castShadow>
        <meshStandardMaterial color="#755f49" roughness={0.78} />
      </RoundedBox>

      <mesh position={[1.05, 0.48, 0.7]} castShadow>
        <cylinderGeometry args={[0.5, 0.56, 0.12, 36]} />
        <meshStandardMaterial color="#5f4937" />
      </mesh>
      <mesh position={[1.05, 0.24, 0.7]} castShadow>
        <cylinderGeometry args={[0.06, 0.06, 0.48, 18]} />
        <meshStandardMaterial color="#2a2a25" />
      </mesh>

      {features.map((feature) => (
        <Hotspot
          key={feature.id}
          feature={feature}
          active={activeFeature === feature.id}
          onSelect={() => onFeatureSelect(feature.id)}
        />
      ))}
    </group>
  );
}

export function SpatialRoomViewer({
  roomName,
  hasBalcony,
}: SpatialRoomViewerProps) {
  const controlsRef = useRef<OrbitControlsImpl | null>(null);
  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const [activeFeatureId, setActiveFeatureId] = useState<SpatialFeatureId | null>(null);
  const [showHotspots, setShowHotspots] = useState(true);

  const features = useMemo<SpatialFeature[]>(
    () => [
      {
        id: "bed",
        label: "King bed",
        description: "Inspect the sleeping area, bed clearance and circulation space.",
        position: [-0.7, 1.22, -0.85],
        camera: [2.8, 2.05, 3.05],
      },
      {
        id: "window",
        label: hasBalcony ? "Balcony & view" : "Window & view",
        description: hasBalcony
          ? "Explore the demonstration balcony and exterior-facing area."
          : "Explore the demonstration window and exterior-facing area.",
        position: [1.85, 1.55, 2.85],
        camera: [1.35, 2.05, 5.0],
      },
      {
        id: "bathroom",
        label: "Bathroom",
        description:
          "Explore the bathroom area in the demonstration environment.",
        position: [-3.0, 1.35, 1.2],
        camera: [-4.65, 2.0, 2.8],
      },
      {
        id: "workspace",
        label: "Workspace",
        description: "Explore the workspace area in the demonstration environment.",
        position: [2.55, 1.18, -1.7],
        camera: [4.65, 2.0, 0.3],
      },
      {
        id: "storage",
        label: "Storage",
        description: "Wardrobe and luggage-storage area.",
        position: [-3.35, 1.55, -2.35],
        camera: [-4.7, 2.0, -0.4],
      },
      {
        id: "entrance",
        label: "Entrance",
        description: "Room entry and main circulation path.",
        position: [3.7, 1.15, 0.7],
        camera: [4.8, 2.1, 3.3],
      },
    ],
    [hasBalcony],
  );

  const activeFeature = features.find((feature) => feature.id === activeFeatureId) ?? null;

  const resetView = useCallback(() => {
    setActiveFeatureId(null);
  }, []);

  const toggleFullscreen = useCallback(async () => {
    const element = wrapperRef.current;
    if (!element) {
      return;
    }

    try {
      if (!document.fullscreenElement) {
        await element.requestFullscreen();
      } else {
        await document.exitFullscreen();
      }
    } catch (error) {
      console.error("Unable to toggle fullscreen:", error);
    }
  }, []);

  const toggleHotspots = useCallback(() => {
    setShowHotspots((current) => {
      if (current) {
        setActiveFeatureId(null);
      }
      return !current;
    });
  }, []);

  return (
    <div
      ref={wrapperRef}
      className="relative h-[72vh] min-h-[620px] max-h-[820px] overflow-hidden rounded-[34px] bg-[#b9c9c2] shadow-[0_35px_100px_rgba(9,34,27,.22)]"
    >
      <Canvas
        shadows
        camera={{ position: DEFAULT_CAMERA, fov: 56, near: 0.1, far: 80 }}
        dpr={[1, 1.6]}
      >
        <color attach="background" args={["#b9c9c2"]} />
        <fog attach="fog" args={["#b9c9c2", 14, 28]} />
        <ambientLight intensity={1.25} />
        <hemisphereLight args={["#dff2f0", "#6f604d", 1.1]} />
        <directionalLight position={[3, 7, 5]} intensity={2.5} castShadow shadow-mapSize={[1536, 1536]} />
        <pointLight position={[-1, 2.9, -1]} intensity={1.3} color="#ffdca3" distance={7} />
        <pointLight position={[2.8, 2.4, 1.8]} intensity={0.8} color="#d9f4ff" distance={6} />

        <PremiumDemoRoom
          features={showHotspots ? features : []}
          activeFeature={activeFeatureId}
          onFeatureSelect={setActiveFeatureId}
          hasBalcony={hasBalcony}
        />

        <CameraRig focus={activeFeature} controlsRef={controlsRef} />
        <OrbitControls
          ref={controlsRef}
          enablePan={false}
          enableDamping
          dampingFactor={0.075}
          minDistance={2.6}
          maxDistance={7.6}
          minPolarAngle={0.68}
          maxPolarAngle={1.48}
          rotateSpeed={0.55}
          zoomSpeed={0.72}
          target={DEFAULT_TARGET}
        />
      </Canvas>

      <div className="pointer-events-none absolute inset-x-0 top-0 flex items-start justify-between gap-3 p-4 sm:p-5">
        <div className="max-w-[75%] rounded-2xl bg-[#0b2857]/92 px-4 py-3 text-white shadow-xl backdrop-blur-xl">
          <p className="text-[10px] font-bold uppercase tracking-[.18em] text-[#d8e9ff]">
            SpatialStay demo environment
          </p>
          <p className="mt-1 truncate text-base font-semibold sm:text-lg">{roomName}</p>
          <p className="mt-1 hidden text-xs text-white/60 sm:block">
            Interactive viewer architecture · not an exact scanned room
          </p>
        </div>

        <div className="pointer-events-auto flex gap-2">
          <button
            type="button"
            onClick={resetView}
            className="grid h-11 w-11 place-items-center rounded-full bg-white/95 text-[#174f9f] shadow-lg transition hover:scale-105"
            aria-label="Reset view"
            title="Reset view"
          >
            <RotateCcw size={18} />
          </button>
          <button
            type="button"
            onClick={toggleFullscreen}
            className="grid h-11 w-11 place-items-center rounded-full bg-white/95 text-[#174f9f] shadow-lg transition hover:scale-105"
            aria-label="Toggle fullscreen"
            title="Toggle fullscreen"
          >
            <Maximize size={18} />
          </button>
        </div>
      </div>

      <div className="absolute inset-x-4 bottom-4 sm:inset-x-5 sm:bottom-5">
        <div className="pointer-events-auto flex gap-2 overflow-x-auto pb-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {features.map((feature) => (
            <button
              key={feature.id}
              type="button"
              onClick={() => setActiveFeatureId(feature.id)}
              className={`shrink-0 rounded-full px-4 py-2 text-xs font-bold shadow-lg backdrop-blur-xl transition sm:text-sm ${
                activeFeatureId === feature.id
                  ? "bg-[#d8e9ff] text-[#0b2857]"
                  : "bg-[#0b2857]/88 text-white hover:bg-[#0b2857]"
              }`}
            >
              {feature.label}
            </button>
          ))}
          <button
            type="button"
            onClick={toggleHotspots}
            className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-white/95 text-[#174f9f] shadow-lg sm:h-10 sm:w-10"
            aria-label={showHotspots ? "Hide hotspots" : "Show hotspots"}
            title={showHotspots ? "Hide hotspots" : "Show hotspots"}
          >
            {showHotspots ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>

        <div className="mt-1 max-w-xl rounded-2xl bg-[#0b2857]/92 p-4 text-white shadow-xl backdrop-blur-xl">
          {activeFeature ? (
            <>
              <p className="text-[10px] font-bold uppercase tracking-[.18em] text-[#d8e9ff]">Focused feature</p>
              <h3 className="mt-1 text-lg font-bold">{activeFeature.label}</h3>
              <p className="mt-1 text-sm leading-6 text-white/68">{activeFeature.description}</p>
            </>
          ) : (
            <>
              <p className="text-sm font-semibold">Drag to look around · scroll or pinch to zoom</p>
              <p className="mt-1 text-xs leading-5 text-white/60">
                Choose a feature below to move smoothly to the most useful viewing angle.
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
