"use client";

import { useEffect, useState } from "react";
import { Plus, Save, X } from "lucide-react";
import { HotelOpsShell } from "@/components/hotel-ops/HotelOpsShell";
import { useHotelOps } from "@/components/hotel-ops/HotelOpsProvider";
import { HotelProfile, hotelOpsApi } from "@/lib/hotel-operations-api";

export default function Page() {
  const { hotelId } = useHotelOps();
  const [profile, setProfile] = useState<HotelProfile | null>(null);
  const [newAmenity, setNewAmenity] = useState("");

  useEffect(() => {
    if (hotelId) {
      hotelOpsApi.profile(hotelId).then(setProfile);
    }
  }, [hotelId]);

  if (!profile) {
    return (
      <HotelOpsShell>
        <div className="hotel-skeleton">Loading profile…</div>
      </HotelOpsShell>
    );
  }

  // Capture the narrowed non-null value for callbacks/async functions.
  // TypeScript does not preserve state-variable narrowing across closures
  // because the state may change before a callback executes.
  const p = profile;

  const field = (key: keyof HotelProfile, value: unknown) => {
    setProfile((current) =>
      current ? { ...current, [key]: value } : current,
    );
  };

  async function saveProfile() {
    setProfile(
      await hotelOpsApi.updateProfile(hotelId, {
        name: p.name,
        description: p.description,
        city: p.city,
        state: p.state,
        country: p.country,
        address: p.address,
        latitude: p.latitude,
        longitude: p.longitude,
        heroImageUrl: p.heroImageUrl,
        contactEmail: p.contactEmail,
        contactPhone: p.contactPhone,
      }),
    );
  }

  async function savePolicies() {
    setProfile(
      await hotelOpsApi.updatePolicies(hotelId, {
        checkInTime: p.checkInTime,
        checkOutTime: p.checkOutTime,
        checkInInstructions: p.checkInInstructions,
        houseRules: p.houseRules,
        childPolicy: p.childPolicy,
        petPolicy: p.petPolicy,
        smokingPolicy: p.smokingPolicy,
      }),
    );
  }

  async function saveAmenities(amenities: string[] = p.amenities) {
    setProfile(await hotelOpsApi.updateAmenities(hotelId, amenities));
  }

  return (
    <HotelOpsShell>
      <div className="hotel-page-head">
        <div>
          <small>PROPERTY EXPERIENCE</small>
          <h1>Hotel profile & policies</h1>
          <p>Keep guest-facing information accurate and operational policies clear.</p>
        </div>
      </div>

      <section className="hotel-panel hotel-form-section">
        <div className="hotel-section-title">
          <div>
            <small>PUBLIC PROFILE</small>
            <h2>Property details</h2>
          </div>
          <button className="hotel-primary compact" onClick={() => void saveProfile()}>
            <Save />
            Save profile
          </button>
        </div>

        <div className="hotel-form-grid three">
          <label>
            Hotel name
            <input value={p.name} onChange={(e) => field("name", e.target.value)} />
          </label>
          <label>
            Contact email
            <input
              value={p.contactEmail ?? ""}
              onChange={(e) => field("contactEmail", e.target.value)}
            />
          </label>
          <label>
            Contact phone
            <input
              value={p.contactPhone ?? ""}
              onChange={(e) => field("contactPhone", e.target.value)}
            />
          </label>
          <label>
            City
            <input value={p.city} onChange={(e) => field("city", e.target.value)} />
          </label>
          <label>
            State
            <input
              value={p.state ?? ""}
              onChange={(e) => field("state", e.target.value)}
            />
          </label>
          <label>
            Country
            <input
              value={p.country}
              onChange={(e) => field("country", e.target.value)}
            />
          </label>
          <label className="span3">
            Address
            <input
              value={p.address ?? ""}
              onChange={(e) => field("address", e.target.value)}
            />
          </label>
          <label className="span3">
            Description
            <textarea
              value={p.description ?? ""}
              onChange={(e) => field("description", e.target.value)}
            />
          </label>
        </div>
      </section>

      <section className="hotel-panel hotel-form-section">
        <div className="hotel-section-title">
          <div>
            <small>AMENITIES</small>
            <h2>Property amenities</h2>
          </div>
        </div>

        <div className="hotel-chip-list">
          {p.amenities.map((amenity) => (
            <span key={amenity}>
              {amenity}
              <button
                onClick={() =>
                  void saveAmenities(p.amenities.filter((item) => item !== amenity))
                }
              >
                <X />
              </button>
            </span>
          ))}
        </div>

        <div className="hotel-add-row">
          <input
            value={newAmenity}
            onChange={(e) => setNewAmenity(e.target.value)}
            placeholder="e.g. Rooftop pool"
          />
          <button
            className="hotel-action"
            onClick={() => {
              const amenity = newAmenity.trim();
              if (amenity && !p.amenities.includes(amenity)) {
                void saveAmenities([...p.amenities, amenity]);
                setNewAmenity("");
              }
            }}
          >
            <Plus />
            Add amenity
          </button>
        </div>
      </section>

      <section className="hotel-panel hotel-form-section">
        <div className="hotel-section-title">
          <div>
            <small>GUEST POLICIES</small>
            <h2>Stay policies</h2>
          </div>
          <button className="hotel-primary compact" onClick={() => void savePolicies()}>
            <Save />
            Save policies
          </button>
        </div>

        <div className="hotel-form-grid">
          <label>
            Check-in time
            <input
              type="time"
              value={(p.checkInTime ?? "").slice(0, 5)}
              onChange={(e) => field("checkInTime", e.target.value)}
            />
          </label>
          <label>
            Check-out time
            <input
              type="time"
              value={(p.checkOutTime ?? "").slice(0, 5)}
              onChange={(e) => field("checkOutTime", e.target.value)}
            />
          </label>
          <label>
            Child policy
            <textarea
              value={p.childPolicy ?? ""}
              onChange={(e) => field("childPolicy", e.target.value)}
            />
          </label>
          <label>
            Pet policy
            <textarea
              value={p.petPolicy ?? ""}
              onChange={(e) => field("petPolicy", e.target.value)}
            />
          </label>
          <label>
            Smoking policy
            <textarea
              value={p.smokingPolicy ?? ""}
              onChange={(e) => field("smokingPolicy", e.target.value)}
            />
          </label>
          <label>
            House rules
            <textarea
              value={p.houseRules ?? ""}
              onChange={(e) => field("houseRules", e.target.value)}
            />
          </label>
          <label className="span2">
            Check-in instructions
            <textarea
              value={p.checkInInstructions ?? ""}
              onChange={(e) => field("checkInInstructions", e.target.value)}
            />
          </label>
        </div>
      </section>
    </HotelOpsShell>
  );
}
