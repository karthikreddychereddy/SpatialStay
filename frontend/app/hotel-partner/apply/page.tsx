"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  ImagePlus,
  Plus,
  Trash2,
} from "lucide-react";

import {
  HotelApplicationPayload,
  ProposedRoom,
  mediaUrl,
  submitHotelApplication,
  uploadPartnerImage,
} from "../../../lib/partner-api";

const blankRoom = (): ProposedRoom => ({
  name: "",
  roomType: "",
  description: "",
  maxGuests: 2,
  totalUnits: 1,
  bedType: "King",
  roomSizeSqFt: 300,
  pricePerNight: 0,
  imageUrls: [],
});

const initial: HotelApplicationPayload = {
  ownerFirstName: "",
  ownerLastName: "",
  ownerEmail: "",
  password: "",
  ownerPhone: "",

  hotelName: "",
  propertyType: "Hotel",
  starCategory: 3,

  city: "",
  state: "",
  country: "India",
  address: "",

  contactPhone: "",
  contactEmail: "",
  website: "",

  startingPrice: 0,
  description: "",
  totalRooms: 1,

  checkInTime: "14:00",
  checkOutTime: "11:00",

  houseRules: "",
  childPolicy: "",
  petPolicy: "",
  smokingPolicy: "",

  amenities: [],
  hotelImageUrls: [],
  rooms: [blankRoom()],
};

const steps = [
  "Owner",
  "Property",
  "Photos",
  "Rooms",
  "Policies",
  "Review",
];

export default function ApplyPage() {
  const [step, setStep] = useState(0);
  const [form, setForm] = useState<HotelApplicationPayload>(initial);
  const [amenity, setAmenity] = useState("");
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState("");
  const [error, setError] = useState("");

  const [done, setDone] = useState<{
    reference: string;
    hotelName: string;
  } | null>(null);

  function setField<K extends keyof HotelApplicationPayload>(
    key: K,
    value: HotelApplicationPayload[K],
  ) {
    setForm((current) => ({
      ...current,
      [key]: value,
    }));
  }

  const valid = useMemo(() => {
    if (step === 0) {
      return Boolean(
        form.ownerFirstName &&
          form.ownerLastName &&
          form.ownerEmail &&
          form.password.length >= 8 &&
          form.ownerPhone,
      );
    }

    if (step === 1) {
      return Boolean(
        form.hotelName &&
          form.propertyType &&
          form.city &&
          form.country &&
          form.address &&
          form.contactPhone &&
          form.contactEmail &&
          form.startingPrice > 0 &&
          form.description,
      );
    }

    if (step === 2) {
      return form.hotelImageUrls.length >= 1;
    }

    if (step === 3) {
      return (
        form.rooms.length > 0 &&
        form.rooms.every(
          (room) =>
            room.name &&
            room.roomType &&
            room.maxGuests > 0 &&
            room.totalUnits > 0 &&
            room.bedType &&
            room.pricePerNight > 0 &&
            room.imageUrls.length > 0,
        )
      );
    }

    return true;
  }, [step, form]);

  async function upload(
    files: FileList | null,
    target: "hotel" | number,
  ) {
    if (!files?.length) {
      return;
    }

    setError("");
    setUploading(target === "hotel" ? "hotel" : `room-${target}`);

    try {
      const urls: string[] = [];

      for (const file of Array.from(files)) {
        const uploadedUrl = await uploadPartnerImage(file);
        urls.push(uploadedUrl);
      }

      if (target === "hotel") {
        setField(
          "hotelImageUrls",
          [...form.hotelImageUrls, ...urls].slice(0, 12),
        );
      } else {
        setForm((current) => ({
          ...current,
          rooms: current.rooms.map((room, index) =>
            index === target
              ? {
                  ...room,
                  imageUrls: [...room.imageUrls, ...urls].slice(0, 8),
                }
              : room,
          ),
        }));
      }
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Image upload failed.",
      );
    } finally {
      setUploading("");
    }
  }

  function roomSet<K extends keyof ProposedRoom>(
    index: number,
    key: K,
    value: ProposedRoom[K],
  ) {
    setForm((current) => ({
      ...current,
      rooms: current.rooms.map((room, roomIndex) =>
        roomIndex === index
          ? {
              ...room,
              [key]: value,
            }
          : room,
      ),
    }));
  }

  async function submit() {
    setBusy(true);
    setError("");

    try {
      const response = await submitHotelApplication(form);

      setDone({
        reference: response.reference,
        hotelName: response.hotelName,
      });
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Application could not be submitted.",
      );
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <main className="min-h-screen bg-[#f7f8fa] pt-28">
        <div className="mx-auto max-w-2xl px-5 py-16">
          <div className="rounded-[28px] border bg-white p-8 text-center shadow-sm">
            <CheckCircle2
              className="mx-auto text-emerald-600"
              size={52}
            />

            <h1 className="mt-5 text-3xl font-bold text-[#17233b]">
              Application submitted
            </h1>

            <p className="mt-3 text-[#66748a]">
              {done.hotelName} is now waiting for SpatialStay Admin
              review.
            </p>

            <div className="mx-auto mt-6 max-w-sm rounded-2xl bg-[#f4f7fb] p-5">
              <p className="text-xs font-bold uppercase tracking-wider text-[#718096]">
                Application reference
              </p>

              <p className="mt-2 text-xl font-bold text-[#173d70]">
                {done.reference}
              </p>

              <p className="mt-3 text-sm font-semibold text-amber-700">
                PENDING APPROVAL
              </p>
            </div>

            <p className="mt-6 text-sm leading-6 text-[#66748a]">
              After approval, use the same owner email and password at
              Hotel Partner Login. Your account will receive
              HOTEL_HEAD access only after Admin approval.
            </p>

            <Link
              href="/hotel/login"
              className="mt-7 inline-block rounded-xl bg-[#2463eb] px-6 py-3 font-bold text-white"
            >
              Hotel Partner Login
            </Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f7f8fa] pt-24 text-[#17233b]">
      <div className="mx-auto max-w-[1100px] px-4 py-8">
        <div className="mb-6 flex items-center justify-between">
          <Link
            href="/hotel-partner"
            className="flex items-center gap-2 text-sm font-bold text-[#53657f]"
          >
            <ArrowLeft size={17} />
            Back
          </Link>

          <span className="text-sm text-[#718096]">
            New property application
          </span>
        </div>

        <div className="grid gap-6 lg:grid-cols-[240px_1fr]">
          <aside className="h-fit rounded-2xl border bg-white p-4">
            {steps.map((stepName, index) => (
              <div
                key={stepName}
                className={`flex items-center gap-3 rounded-xl px-3 py-3 text-sm font-bold ${
                  index === step
                    ? "bg-[#edf3ff] text-[#2456a6]"
                    : index < step
                      ? "text-emerald-700"
                      : "text-[#8390a3]"
                }`}
              >
                <span className="grid h-7 w-7 place-items-center rounded-full border">
                  {index < step ? "✓" : index + 1}
                </span>

                {stepName}
              </div>
            ))}
          </aside>

          <section className="rounded-[24px] border bg-white p-6 shadow-sm sm:p-8">
            {error && (
              <div className="mb-5 rounded-xl border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700">
                {error}
              </div>
            )}

            {step === 0 && (
              <Block
                title="Who will manage this property?"
                sub="These credentials become the Hotel Head login only after Admin approval."
              >
                <Grid>
                  <Input
                    label="First name"
                    value={form.ownerFirstName}
                    on={(value) => setField("ownerFirstName", value)}
                  />

                  <Input
                    label="Last name"
                    value={form.ownerLastName}
                    on={(value) => setField("ownerLastName", value)}
                  />

                  <Input
                    label="Owner email"
                    type="email"
                    value={form.ownerEmail}
                    on={(value) => setField("ownerEmail", value)}
                  />

                  <Input
                    label="Phone"
                    value={form.ownerPhone}
                    on={(value) => setField("ownerPhone", value)}
                  />

                  <Input
                    label="Create password"
                    type="password"
                    value={form.password}
                    on={(value) => setField("password", value)}
                    hint="Minimum 8 characters. Never shown to Admin."
                  />
                </Grid>
              </Block>
            )}

            {step === 1 && (
              <Block
                title="Tell us about the hotel"
                sub="This is the core information Admin will verify before onboarding."
              >
                <Grid>
                  <Input
                    label="Hotel name"
                    value={form.hotelName}
                    on={(value) => setField("hotelName", value)}
                  />

                  <Input
                    label="Property type"
                    value={form.propertyType}
                    on={(value) => setField("propertyType", value)}
                  />

                  <Input
                    label="Star category"
                    type="number"
                    value={form.starCategory}
                    on={(value) =>
                      setField("starCategory", Number(value))
                    }
                  />

                  <Input
                    label="Total rooms"
                    type="number"
                    value={form.totalRooms}
                    on={(value) =>
                      setField("totalRooms", Number(value))
                    }
                  />

                  <Input
                    label="City"
                    value={form.city}
                    on={(value) => setField("city", value)}
                  />

                  <Input
                    label="State"
                    value={form.state}
                    on={(value) => setField("state", value)}
                  />

                  <Input
                    label="Country"
                    value={form.country}
                    on={(value) => setField("country", value)}
                  />

                  <Input
                    label="Full address"
                    value={form.address}
                    on={(value) => setField("address", value)}
                  />

                  <Input
                    label="Hotel contact email"
                    type="email"
                    value={form.contactEmail}
                    on={(value) => setField("contactEmail", value)}
                  />

                  <Input
                    label="Hotel phone"
                    value={form.contactPhone}
                    on={(value) => setField("contactPhone", value)}
                  />

                  <Input
                    label="Website (optional)"
                    value={form.website}
                    on={(value) => setField("website", value)}
                  />

                  <Input
                    label="Starting price / night (₹)"
                    type="number"
                    value={form.startingPrice}
                    on={(value) =>
                      setField("startingPrice", Number(value))
                    }
                  />
                </Grid>

                <Area
                  label="Property description"
                  value={form.description}
                  on={(value) => setField("description", value)}
                />

                <div className="mt-5">
                  <label className="text-sm font-bold">
                    Amenities
                  </label>

                  <div className="mt-2 flex gap-2">
                    <input
                      className="field"
                      value={amenity}
                      onChange={(event) =>
                        setAmenity(event.target.value)
                      }
                      placeholder="Wi-Fi, Pool, Parking…"
                    />

                    <button
                      type="button"
                      className="secondary"
                      onClick={() => {
                        const value = amenity.trim();

                        if (!value) {
                          return;
                        }

                        setField("amenities", [
                          ...form.amenities,
                          value,
                        ]);

                        setAmenity("");
                      }}
                    >
                      Add
                    </button>
                  </div>

                  <div className="mt-2 flex flex-wrap gap-2">
                    {form.amenities.map((item) => (
                      <button
                        type="button"
                        key={item}
                        onClick={() =>
                          setField(
                            "amenities",
                            form.amenities.filter(
                              (existing) => existing !== item,
                            ),
                          )
                        }
                        className="rounded-full bg-[#edf3ff] px-3 py-1 text-xs font-bold text-[#315f9c]"
                      >
                        {item} ×
                      </button>
                    ))}
                  </div>
                </div>
              </Block>
            )}

            {step === 2 && (
              <Block
                title="Show Admin the property"
                sub="Upload clear exterior, lobby, common-area and facility photos. The first photo becomes the hotel cover after approval."
              >
                <Upload
                  busy={uploading === "hotel"}
                  onFiles={(files) => upload(files, "hotel")}
                />

                <Gallery
                  urls={form.hotelImageUrls}
                  remove={(index) =>
                    setField(
                      "hotelImageUrls",
                      form.hotelImageUrls.filter(
                        (_, currentIndex) =>
                          currentIndex !== index,
                      ),
                    )
                  }
                />
              </Block>
            )}

            {step === 3 && (
              <Block
                title="Rooms and inventory"
                sub="Add the room types guests will eventually see. Every room type needs at least one image."
              >
                {form.rooms.map((room, index) => (
                  <div
                    key={index}
                    className="mb-5 rounded-2xl border border-[#dde4ee] p-5"
                  >
                    <div className="flex justify-between">
                      <h3 className="font-bold">
                        Room type {index + 1}
                      </h3>

                      {form.rooms.length > 1 && (
                        <button
                          type="button"
                          onClick={() =>
                            setField(
                              "rooms",
                              form.rooms.filter(
                                (_, roomIndex) =>
                                  roomIndex !== index,
                              ),
                            )
                          }
                          className="text-red-600"
                          aria-label={`Remove room type ${index + 1}`}
                        >
                          <Trash2 size={18} />
                        </button>
                      )}
                    </div>

                    <Grid>
                      <Input
                        label="Room name"
                        value={room.name}
                        on={(value) =>
                          roomSet(index, "name", value)
                        }
                      />

                      <Input
                        label="Room type"
                        value={room.roomType}
                        on={(value) =>
                          roomSet(index, "roomType", value)
                        }
                      />

                      <Input
                        label="Max guests"
                        type="number"
                        value={room.maxGuests}
                        on={(value) =>
                          roomSet(
                            index,
                            "maxGuests",
                            Number(value),
                          )
                        }
                      />

                      <Input
                        label="Number of units"
                        type="number"
                        value={room.totalUnits}
                        on={(value) =>
                          roomSet(
                            index,
                            "totalUnits",
                            Number(value),
                          )
                        }
                      />

                      <Input
                        label="Bed type"
                        value={room.bedType}
                        on={(value) =>
                          roomSet(index, "bedType", value)
                        }
                      />

                      <Input
                        label="Room size sq ft"
                        type="number"
                        value={room.roomSizeSqFt}
                        on={(value) =>
                          roomSet(
                            index,
                            "roomSizeSqFt",
                            Number(value),
                          )
                        }
                      />

                      <Input
                        label="Price / night (₹)"
                        type="number"
                        value={room.pricePerNight}
                        on={(value) =>
                          roomSet(
                            index,
                            "pricePerNight",
                            Number(value),
                          )
                        }
                      />
                    </Grid>

                    <Area
                      label="Room description"
                      value={room.description}
                      on={(value) =>
                        roomSet(index, "description", value)
                      }
                    />

                    <div className="mt-4">
                      <Upload
                        compact
                        busy={uploading === `room-${index}`}
                        onFiles={(files) =>
                          upload(files, index)
                        }
                      />

                      <Gallery
                        urls={room.imageUrls}
                        remove={(imageIndex) =>
                          roomSet(
                            index,
                            "imageUrls",
                            room.imageUrls.filter(
                              (_, currentIndex) =>
                                currentIndex !== imageIndex,
                            ),
                          )
                        }
                      />
                    </div>
                  </div>
                ))}

                <button
                  type="button"
                  onClick={() =>
                    setField("rooms", [
                      ...form.rooms,
                      blankRoom(),
                    ])
                  }
                  className="secondary"
                >
                  <Plus className="mr-2 inline" size={16} />
                  Add another room type
                </button>
              </Block>
            )}

            {step === 4 && (
              <Block
                title="Stay policies"
                sub="Give Admin enough operational information to review the property accurately."
              >
                <Grid>
                  <Input
                    label="Check-in time"
                    type="time"
                    value={form.checkInTime}
                    on={(value) =>
                      setField("checkInTime", value)
                    }
                  />

                  <Input
                    label="Check-out time"
                    type="time"
                    value={form.checkOutTime}
                    on={(value) =>
                      setField("checkOutTime", value)
                    }
                  />
                </Grid>

                <Area
                  label="House rules"
                  value={form.houseRules}
                  on={(value) => setField("houseRules", value)}
                />

                <Area
                  label="Children policy"
                  value={form.childPolicy}
                  on={(value) => setField("childPolicy", value)}
                />

                <Area
                  label="Pet policy"
                  value={form.petPolicy}
                  on={(value) => setField("petPolicy", value)}
                />

                <Area
                  label="Smoking policy"
                  value={form.smokingPolicy}
                  on={(value) =>
                    setField("smokingPolicy", value)
                  }
                />
              </Block>
            )}

            {step === 5 && (
              <Block
                title="Review before submission"
                sub="Admin will receive this complete application. Nothing becomes publicly bookable until approval."
              >
                <div className="grid gap-4 sm:grid-cols-2">
                  <Summary
                    label="Owner"
                    value={`${form.ownerFirstName} ${form.ownerLastName} · ${form.ownerEmail}`}
                  />

                  <Summary
                    label="Property"
                    value={`${form.hotelName} · ${form.propertyType} · ${form.city}`}
                  />

                  <Summary
                    label="Hotel photos"
                    value={`${form.hotelImageUrls.length} uploaded`}
                  />

                  <Summary
                    label="Room types"
                    value={`${form.rooms.length} configured`}
                  />

                  <Summary
                    label="Inventory"
                    value={`${form.rooms.reduce(
                      (total, room) =>
                        total + room.totalUnits,
                      0,
                    )} room units`}
                  />

                  <Summary
                    label="Starting price"
                    value={`₹${form.startingPrice.toLocaleString(
                      "en-IN",
                    )}`}
                  />
                </div>

                <div className="mt-5 rounded-xl bg-[#f5f7fb] p-4 text-sm leading-6 text-[#5f6d84]">
                  By submitting, you confirm that the property
                  information and images are accurate and that you are
                  authorized to list this property. Approval is
                  performed by SpatialStay Admin.
                </div>
              </Block>
            )}

            <div className="mt-8 flex justify-between border-t pt-5">
              <button
                type="button"
                disabled={step === 0 || busy}
                onClick={() =>
                  setStep((current) => current - 1)
                }
                className="secondary disabled:opacity-40"
              >
                <ArrowLeft
                  className="mr-2 inline"
                  size={16}
                />
                Back
              </button>

              {step < steps.length - 1 ? (
                <button
                  type="button"
                  disabled={!valid}
                  onClick={() =>
                    setStep((current) => current + 1)
                  }
                  className="primary disabled:opacity-40"
                >
                  Continue
                  <ArrowRight
                    className="ml-2 inline"
                    size={16}
                  />
                </button>
              ) : (
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void submit()}
                  className="primary disabled:opacity-50"
                >
                  {busy
                    ? "Submitting…"
                    : "Submit for Admin review"}
                </button>
              )}
            </div>
          </section>
        </div>
      </div>

      <style jsx global>{`
        .field {
          width: 100%;
          border: 1px solid #d8e0eb;
          border-radius: 12px;
          padding: 11px 12px;
          background: #ffffff;
          outline: none;
        }

        .field:focus {
          border-color: #4d7fe5;
          box-shadow: 0 0 0 3px #eaf1ff;
        }

        .primary {
          border-radius: 12px;
          background: #2463eb;
          color: white;
          padding: 11px 18px;
          font-weight: 700;
        }

        .secondary {
          border: 1px solid #d5deea;
          border-radius: 12px;
          background: white;
          padding: 10px 16px;
          font-weight: 700;
          color: #3d536f;
        }
      `}</style>
    </main>
  );
}

function Block({
  title,
  sub,
  children,
}: {
  title: string;
  sub: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h1 className="text-3xl font-bold tracking-tight">
        {title}
      </h1>

      <p className="mt-2 mb-7 text-[#6b788c]">
        {sub}
      </p>

      {children}
    </div>
  );
}

function Grid({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {children}
    </div>
  );
}

function Input({
  label,
  value,
  on,
  type = "text",
  hint,
}: {
  label: string;
  value: string | number;
  on: (value: string) => void;
  type?: string;
  hint?: string;
}) {
  return (
    <label className="block text-sm font-bold">
      {label}

      <input
        className="field mt-2"
        type={type}
        value={value}
        onChange={(event) => on(event.target.value)}
      />

      {hint && (
        <span className="mt-1 block text-xs font-normal text-[#8491a3]">
          {hint}
        </span>
      )}
    </label>
  );
}

function Area({
  label,
  value,
  on,
}: {
  label: string;
  value: string;
  on: (value: string) => void;
}) {
  return (
    <label className="mt-4 block text-sm font-bold">
      {label}

      <textarea
        className="field mt-2 min-h-24"
        value={value}
        onChange={(event) => on(event.target.value)}
      />
    </label>
  );
}

function Upload({
  onFiles,
  busy,
  compact = false,
}: {
  onFiles: (files: FileList | null) => void;
  busy: boolean;
  compact?: boolean;
}) {
  return (
    <label
      className={`flex cursor-pointer items-center justify-center rounded-2xl border-2 border-dashed border-[#cfd9e8] bg-[#f8faff] ${
        compact ? "p-4" : "p-8"
      }`}
    >
      <ImagePlus className="mr-2" size={20} />

      <span className="font-bold text-[#315f9c]">
        {busy ? "Uploading…" : "Upload images"}
      </span>

      <input
        hidden
        multiple
        accept="image/jpeg,image/png,image/webp"
        type="file"
        onChange={(event) => onFiles(event.target.files)}
      />
    </label>
  );
}

function Gallery({
  urls,
  remove,
}: {
  urls: string[];
  remove: (index: number) => void;
}) {
  return (
    <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
      {urls.map((url, index) => (
        <div
          key={`${url}-${index}`}
          className="relative overflow-hidden rounded-xl border"
        >
          <img
            src={mediaUrl(url)}
            alt={`Application upload ${index + 1}`}
            className="h-28 w-full object-cover"
          />

          <button
            type="button"
            onClick={() => remove(index)}
            className="absolute right-2 top-2 grid h-7 w-7 place-items-center rounded-full bg-white text-red-600 shadow"
            aria-label={`Remove image ${index + 1}`}
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}

function Summary({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-xl border p-4">
      <p className="text-xs font-bold uppercase tracking-wide text-[#8491a3]">
        {label}
      </p>

      <p className="mt-2 font-bold">
        {value}
      </p>
    </div>
  );
}