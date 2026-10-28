"use client";

import { useParams } from "next/navigation";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  ArrowUpRight,
  CheckCircle2,
  Images,
  Search,
  ShieldAlert,
} from "lucide-react";

import {
  adminApi,
  AdminAnalytics,
} from "@/lib/admin-api";

import { PageHeading } from "@/components/admin/AdminShell";
import { mediaUrl } from "../../../lib/partner-api";

type Row = Record<string, unknown>;

type Section =
  | "applications"
  | "hotels"
  | "users"
  | "bookings"
  | "payments"
  | "refunds"
  | "reconciliation"
  | "analytics"
  | "audit";

type Column = {
  key: string;
  label: string;
  render?: "status" | "date" | "money" | "boolean";
};

type DecisionAction =
  | "approve"
  | "reject"
  | "hotel"
  | "user"
  | "backfill";

type Decision = {
  action: DecisionAction;
  row: Row;
  nextStatus?: string;
};

const columns: Record<
  Exclude<Section, "analytics">,
  Column[]
> = {
  applications: [
    {
      key: "hotelName",
      label: "Hotel",
    },
    {
      key: "applicantName",
      label: "Applicant",
    },
    {
      key: "city",
      label: "City",
    },
    {
      key: "status",
      label: "Status",
      render: "status",
    },
    {
      key: "createdAt",
      label: "Submitted",
      render: "date",
    },
  ],

  hotels: [
    {
      key: "name",
      label: "Hotel",
    },
    {
      key: "city",
      label: "City",
    },
    {
      key: "country",
      label: "Country",
    },
    {
      key: "status",
      label: "Status",
      render: "status",
    },
    {
      key: "verified",
      label: "Verified",
      render: "boolean",
    },
  ],

  users: [
    {
      key: "email",
      label: "Account",
    },
    {
      key: "role",
      label: "Role",
    },
    {
      key: "status",
      label: "Status",
      render: "status",
    },
    {
      key: "emailVerified",
      label: "Verified",
      render: "boolean",
    },
    {
      key: "createdAt",
      label: "Joined",
      render: "date",
    },
  ],

  bookings: [
    {
      key: "reference",
      label: "Reference",
    },
    {
      key: "hotelName",
      label: "Hotel",
    },
    {
      key: "customerEmail",
      label: "Customer",
    },
    {
      key: "checkIn",
      label: "Check-in",
      render: "date",
    },
    {
      key: "checkOut",
      label: "Check-out",
      render: "date",
    },
    {
      key: "totalAmount",
      label: "Amount",
      render: "money",
    },
    {
      key: "status",
      label: "Status",
      render: "status",
    },
  ],

  payments: [
    {
      key: "bookingReference",
      label: "Booking",
    },
    {
      key: "customerEmail",
      label: "Customer",
    },
    {
      key: "providerPaymentId",
      label: "Provider payment",
    },
    {
      key: "amount",
      label: "Amount",
      render: "money",
    },
    {
      key: "status",
      label: "Status",
      render: "status",
    },
    {
      key: "createdAt",
      label: "Created",
      render: "date",
    },
  ],

  refunds: [
    {
      key: "bookingReference",
      label: "Booking",
    },
    {
      key: "customerEmail",
      label: "Customer",
    },
    {
      key: "providerRefundId",
      label: "Provider refund",
    },
    {
      key: "refundAmount",
      label: "Amount",
      render: "money",
    },
    {
      key: "status",
      label: "Status",
      render: "status",
    },
    {
      key: "createdAt",
      label: "Created",
      render: "date",
    },
  ],

  reconciliation: [
    {
      key: "id",
      label: "Case",
    },
    {
      key: "providerPaymentId",
      label: "Provider payment",
    },
    {
      key: "bookingStatusAtDiscovery",
      label: "Booking at discovery",
      render: "status",
    },
    {
      key: "status",
      label: "Review state",
      render: "status",
    },
    {
      key: "discoveredAt",
      label: "Discovered",
      render: "date",
    },
  ],

  audit: [
    {
      key: "action",
      label: "Action",
    },
    {
      key: "adminEmail",
      label: "Administrator",
    },
    {
      key: "targetType",
      label: "Resource",
    },
    {
      key: "targetId",
      label: "Resource ID",
    },
    {
      key: "createdAt",
      label: "When",
      render: "date",
    },
  ],
};

const descriptions: Record<
  Section,
  {
    title: string;
    description: string;
  }
> = {
  applications: {
    title: "Hotel applications",
    description:
      "Review property and applicant details before approving new hotels.",
  },

  hotels: {
    title: "Hotels",
    description:
      "Manage platform hotel visibility and suspension status.",
  },

  users: {
    title: "User accounts",
    description:
      "Inspect platform accounts and manage non-admin access.",
  },

  bookings: {
    title: "Bookings",
    description:
      "Read-only oversight of confirmed, cancelled and pending reservations.",
  },

  payments: {
    title: "Payments",
    description:
      "Authoritative payment attempts and provider references. No manual status overrides.",
  },

  refunds: {
    title: "Refunds",
    description:
      "Provider-backed refund history. Refund initiation is not exposed in this portal yet.",
  },

  reconciliation: {
    title: "Reconciliation",
    description:
      "Captured-payment exceptions requiring investigation. This queue does not authorize refunds or booking confirmation.",
  },

  analytics: {
    title: "Platform analytics",
    description:
      "Aggregated operational metrics derived from backend records.",
  },

  audit: {
    title: "Admin audit history",
    description:
      "Track privileged platform changes and their responsible administrators.",
  },
};

const sections =
  Object.keys(descriptions) as Section[];

const loadBySection = {
  applications: adminApi.applications,
  hotels: adminApi.hotels,
  users: adminApi.users,
  bookings: adminApi.bookings,
  payments: adminApi.payments,
  refunds: adminApi.refunds,
  reconciliation: adminApi.reconciliation,
  analytics: adminApi.analytics,
  audit: adminApi.audit,
};

function show(
  value: unknown,
  render?: Column["render"],
  currency = "INR",
) {
  if (
    value === null ||
    value === undefined ||
    value === ""
  ) {
    return (
      <span className="sa-muted">
        —
      </span>
    );
  }

  if (render === "boolean") {
    return value
      ? "Yes"
      : "No";
  }

  if (render === "date") {
    const parsed =
      new Date(
        String(value),
      );

    return Number.isNaN(
      parsed.getTime(),
    )
      ? String(value)
      : parsed.toLocaleDateString(
          "en-IN",
          {
            day: "2-digit",
            month: "short",
            year: "numeric",
          },
        );
  }

  if (render === "money") {
    return new Intl.NumberFormat(
      "en-IN",
      {
        style: "currency",
        currency,
      },
    ).format(
      Number(value),
    );
  }

  if (render === "status") {
    const status =
      String(value);

    const cls =
      /ACTIVE|CONFIRMED|SUCCEEDED|APPROVED/.test(
        status,
      )
        ? "success"
        : /PENDING|REVIEW_REQUIRED/.test(
              status,
            )
          ? "warning"
          : /DISABLED|SUSPENDED|FAILED|REJECTED|CANCELLED|EXPIRED/.test(
                status,
              )
            ? "danger"
            : "neutral";

    return (
      <span
        className={
          `sa-badge sa-badge-${cls}`
        }
      >
        {status.replaceAll(
          "_",
          " ",
        )}
      </span>
    );
  }

  return String(value);
}

function RowDetails({
  row,
  section,
}: {
  row: Row;
  section: Section;
}) {
  if (
    section ===
    "applications"
  ) {
    const images =
      (
        row.hotelImageUrls as
          | string[]
          | undefined
      ) ?? [];

    const rooms =
      (
        row.rooms as
          | Array<
              Record<
                string,
                unknown
              >
            >
          | undefined
      ) ?? [];

    const hidden =
      new Set([
        "description",
        "hotelImageUrls",
        "rooms",
        "amenities",
      ]);

    return (
      <div className="sa-details">
        <h3>
          Application details
        </h3>

        <div className="sa-details-grid">
          {Object.keys(row)
            .filter(
              (key) =>
                !hidden.has(
                  key,
                ),
            )
            .map(
              (key) => (
                <div key={key}>
                  <span>
                    {key.replace(
                      /([A-Z])/g,
                      " $1",
                    )}
                  </span>

                  <strong>
                    {show(
                      row[key],
                      key.endsWith(
                        "At",
                      )
                        ? "date"
                        : undefined,
                    )}
                  </strong>
                </div>
              ),
            )}
        </div>

        {row.description ? (
          <p className="sa-description">
            {String(
              row.description,
            )}
          </p>
        ) : null}

        <h3>
          Hotel gallery
        </h3>

        <div
          style={{
            display: "grid",
            gridTemplateColumns:
              "repeat(auto-fill,minmax(150px,1fr))",
            gap: 10,
          }}
        >
          {images.map(
            (
              url,
              index,
            ) => (
              <img
                key={url}
                src={
                  mediaUrl(
                    url,
                  )
                }
                alt={
                  `Hotel ${index + 1}`
                }
                style={{
                  width: "100%",
                  height: 110,
                  objectFit:
                    "cover",
                  borderRadius: 12,
                }}
              />
            ),
          )}
        </div>

        <h3>
          Amenities
        </h3>

        <p>
          {(
            (
              row.amenities as
                | string[]
                | undefined
            ) ?? []
          ).join(" · ") ||
            "—"}
        </p>

        <h3>
          Proposed rooms
        </h3>

        {rooms.map(
          (
            room,
            index,
          ) => (
            <div
              key={String(
                room.id ??
                  index,
              )}
              style={{
                marginTop: 12,
                padding: 14,
                border:
                  "1px solid #dfe5ee",
                borderRadius: 14,
              }}
            >
              <strong>
                {String(
                  room.name ??
                    "Room",
                )}
              </strong>

              <p>
                {String(
                  room.roomType ??
                    "",
                )}{" "}
                ·{" "}
                {String(
                  room.maxGuests ??
                    "",
                )}{" "}
                guests ·{" "}
                {String(
                  room.totalUnits ??
                    "",
                )}{" "}
                units · ₹
                {Number(
                  room.pricePerNight ??
                    0,
                ).toLocaleString(
                  "en-IN",
                )}
                /night
              </p>

              {room.description ? (
                <p className="sa-description">
                  {String(
                    room.description,
                  )}
                </p>
              ) : null}

              <div
                style={{
                  display: "flex",
                  gap: 8,
                  overflowX:
                    "auto",
                }}
              >
                {(
                  (
                    room.imageUrls as
                      | string[]
                      | undefined
                  ) ?? []
                ).map(
                  (
                    url,
                    imageIndex,
                  ) => (
                    <img
                      key={url}
                      src={
                        mediaUrl(
                          url,
                        )
                      }
                      alt={
                        `Room ${index + 1} image ${imageIndex + 1}`
                      }
                      style={{
                        width: 140,
                        height: 95,
                        objectFit:
                          "cover",
                        borderRadius: 10,
                      }}
                    />
                  ),
                )}
              </div>
            </div>
          ),
        )}
      </div>
    );
  }

  const keys =
    Object.keys(row).filter(
      (key) =>
        ![
          "description",
        ].includes(key),
    );

  return (
    <div className="sa-details">
      <h3>
        Record details
      </h3>

      <div className="sa-details-grid">
        {keys.map(
          (key) => (
            <div key={key}>
              <span>
                {key.replace(
                  /([A-Z])/g,
                  " $1",
                )}
              </span>

              <strong>
                {show(
                  row[key],
                  key
                    .toLowerCase()
                    .includes(
                      "at",
                    ) &&
                    key.endsWith(
                      "At",
                    )
                    ? "date"
                    : undefined,
                )}
              </strong>
            </div>
          ),
        )}
      </div>
    </div>
  );
}

function AnalyticsPanel({
  data,
}: {
  data: AdminAnalytics;
}) {
  const counts: Array<
    [string, number]
  > = [
    [
      "Hotels",
      data.hotels,
    ],
    [
      "Users",
      data.users,
    ],
    [
      "Customers",
      data.customers,
    ],
    [
      "Hotel heads",
      data.hotelHeads,
    ],
    [
      "Hotel staff",
      data.hotelStaff,
    ],
    [
      "Bookings",
      data.bookings,
    ],
    [
      "Confirmed bookings",
      data.confirmedBookings,
    ],
    [
      "Cancelled bookings",
      data.cancelledBookings,
    ],
    [
      "Payment attempts",
      data.paymentAttempts,
    ],
    [
      "Successful payments",
      data.successfulPayments,
    ],
    [
      "Refunds",
      data.refunds,
    ],
    [
      "Successful refunds",
      data.successfulRefunds,
    ],
    [
      "Pending applications",
      data.pendingApplications,
    ],
    [
      "Reconciliation cases",
      data.reconciliationCases,
    ],
  ];

  return (
    <>
      <div className="sa-analytics-grid">
        {counts.map(
          ([
            label,
            value,
          ]) => (
            <div
              className="sa-stat"
              key={label}
            >
              <span>
                {label}
              </span>

              <strong>
                {value.toLocaleString(
                  "en-IN",
                )}
              </strong>
            </div>
          ),
        )}
      </div>

      <div className="sa-panel sa-analytics-note">
        <h2>
          Financial totals
        </h2>

        <p>
          These totals are
          sourced from backend
          records. Amounts are
          not converted between
          currencies; use only
          when all included
          records share the same
          currency.
        </p>

        <div className="sa-analytics-grid">
          <div className="sa-stat">
            <span>
              Confirmed booking
              value
            </span>

            <strong>
              {Number(
                data.confirmedBookingValue,
              ).toLocaleString(
                "en-IN",
              )}
            </strong>
          </div>

          <div className="sa-stat">
            <span>
              Successful payment
              value
            </span>

            <strong>
              {Number(
                data.successfulPaymentValue,
              ).toLocaleString(
                "en-IN",
              )}
            </strong>
          </div>

          <div className="sa-stat">
            <span>
              Successful refund
              value
            </span>

            <strong>
              {Number(
                data.successfulRefundValue,
              ).toLocaleString(
                "en-IN",
              )}
            </strong>
          </div>
        </div>
      </div>
    </>
  );
}

export default function AdminSectionPage() {
  const params =
    useParams<{
      section: string;
    }>();

  const section =
    params.section as Section;

  const valid =
    sections.includes(
      section,
    );

  const [
    records,
    setRecords,
  ] =
    useState<Row[]>([]);

  const [
    analytics,
    setAnalytics,
  ] =
    useState<AdminAnalytics | null>(
      null,
    );

  const [
    selected,
    setSelected,
  ] =
    useState<Row | null>(
      null,
    );

  const [
    query,
    setQuery,
  ] =
    useState("");

  const [
    statusFilter,
    setStatusFilter,
  ] =
    useState("ALL");

  const [
    error,
    setError,
  ] =
    useState("");

  const [
    notice,
    setNotice,
  ] =
    useState("");

  const [
    busy,
    setBusy,
  ] =
    useState(true);

  const [
    working,
    setWorking,
  ] =
    useState(false);

  const [
    decision,
    setDecision,
  ] =
    useState<Decision | null>(
      null,
    );

  const [
    note,
    setNote,
  ] =
    useState("");

  const load =
    useCallback(
      async () => {
        if (!valid) {
          return;
        }

        setBusy(true);
        setError("");

        try {
          if (
            section ===
            "analytics"
          ) {
            setAnalytics(
              await adminApi.analytics(),
            );
          } else {
            const result =
              await loadBySection[
                section
              ]();

            setRecords(
              result as unknown as Row[],
            );
          }
        } catch (err) {
          setError(
            err instanceof Error
              ? err.message
              : "Could not load records.",
          );
        } finally {
          setBusy(false);
        }
      },
      [
        section,
        valid,
      ],
    );

  useEffect(
    () => {
      void load();

      setSelected(null);
      setQuery("");
      setStatusFilter(
        "ALL",
      );
    },
    [load],
  );

  const statuses =
    useMemo(
      () =>
        Array.from(
          new Set(
            records
              .map(
                (row) =>
                  String(
                    row.status ??
                      "",
                  ),
              )
              .filter(Boolean),
          ),
        ),
      [records],
    );

  const filtered =
    useMemo(
      () =>
        records.filter(
          (row) => {
            if (
              statusFilter !==
                "ALL" &&
              row.status !==
                statusFilter
            ) {
              return false;
            }

            return (
              !query ||
              JSON.stringify(
                row,
              )
                .toLowerCase()
                .includes(
                  query.toLowerCase(),
                )
            );
          },
        ),
      [
        records,
        query,
        statusFilter,
      ],
    );

  async function confirm() {
    if (!decision) {
      return;
    }

    setWorking(true);
    setError("");
    setNotice("");

    try {
      if (
        decision.action ===
          "approve" ||
        decision.action ===
          "reject"
      ) {
        await adminApi.decideApplication(
          Number(
            decision.row.id,
          ),
          decision.action,
          note.trim(),
        );
      } else if (
        decision.action ===
        "hotel"
      ) {
        await adminApi.setHotelStatus(
          Number(
            decision.row.id,
          ),
          decision.nextStatus as
            | "ACTIVE"
            | "SUSPENDED",
        );
      } else {
        await adminApi.setUserStatus(
          String(
            decision.row
              .publicId,
          ),
          decision.nextStatus as
            | "ACTIVE"
            | "DISABLED",
        );
      }

      if (
        decision.action ===
        "backfill"
      ) {
        setNotice(
          "Gallery backfill completed successfully. The approved hotel and room galleries were restored from the original application.",
        );
      } else {
      }

      setDecision(null);
      setSelected(null);
      setNote("");

      await load();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "The action could not be completed.",
      );
    } finally {
      setWorking(false);
    }
  }

  if (!valid) {
    return (
      <div className="sa-panel sa-state">
        Admin section not
        found.
      </div>
    );
  }

  const {
    title,
    description,
  } =
    descriptions[
      section
    ];

  return (
    <>
      <PageHeading
        title={title}
        description={
          description
        }
        refresh={() =>
          void load()
        }
        refreshing={busy}
      />

      {error && (
        <div
          className="sa-error"
          role="alert"
        >
          {error}
        </div>
      )}

      {notice && (
        <div
          className="sa-success"
          role="status"
        >
          <CheckCircle2
            size={18}
          />

          {notice}
        </div>
      )}

      {section ===
        "reconciliation" && (
        <div className="sa-caution">
          <ShieldAlert
            size={19}
          />

          Review-only queue:
          no automatic
          refunds, booking
          confirmations or
          provider status
          changes.
        </div>
      )}

      {busy &&
      !records.length &&
      !analytics ? (
        <div className="sa-panel sa-state">
          Loading{" "}
          {title.toLowerCase()}
          …
        </div>
      ) : section ===
        "analytics" ? (
        analytics ? (
          <AnalyticsPanel
            data={analytics}
          />
        ) : (
          <div className="sa-panel sa-state">
            No analytics
            available.
          </div>
        )
      ) : (
        <div className="sa-panel sa-table-panel">
          <div className="sa-table-tools">
            <div className="sa-search">
              <Search
                size={18}
              />

              <input
                aria-label="Search records"
                placeholder="Search records…"
                value={query}
                onChange={(
                  event,
                ) =>
                  setQuery(
                    event.target
                      .value,
                  )
                }
              />
            </div>

            {statuses.length >
              0 && (
              <select
                aria-label="Filter by status"
                value={
                  statusFilter
                }
                onChange={(
                  event,
                ) =>
                  setStatusFilter(
                    event.target
                      .value,
                  )
                }
              >
                <option value="ALL">
                  All statuses
                </option>

                {statuses.map(
                  (status) => (
                    <option
                      key={
                        status
                      }
                      value={
                        status
                      }
                    >
                      {status.replaceAll(
                        "_",
                        " ",
                      )}
                    </option>
                  ),
                )}
              </select>
            )}

            <span className="sa-result-count">
              {filtered.length}{" "}
              records
            </span>
          </div>

          <div className="sa-table-scroll">
            <table>
              <thead>
                <tr>
                  {columns[
                    section
                  ].map(
                    (col) => (
                      <th
                        key={
                          col.key
                        }
                      >
                        {
                          col.label
                        }
                      </th>
                    ),
                  )}

                  <th>
                    Details
                  </th>
                </tr>
              </thead>

              <tbody>
                {filtered.map(
                  (
                    row,
                    index,
                  ) => (
                    <tr
                      key={String(
                        row.publicId ??
                          row.id ??
                          index,
                      )}
                    >
                      {columns[
                        section
                      ].map(
                        (
                          col,
                        ) => (
                          <td
                            key={
                              col.key
                            }
                          >
                            {show(
                              row[
                                col
                                  .key
                              ],
                              col.render,
                              String(
                                row.currency ??
                                  "INR",
                              ),
                            )}
                          </td>
                        ),
                      )}

                      <td>
                        <button
                          className="sa-text-action"
                          onClick={() =>
                            setSelected(
                              row,
                            )
                          }
                        >
                          View

                          <ArrowUpRight
                            size={
                              14
                            }
                          />
                        </button>
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>

          {!filtered.length && (
            <div className="sa-empty">
              No matching
              records found.
            </div>
          )}
        </div>
      )}

      {selected && (
        <div
          className="sa-modal-backdrop"
          role="presentation"
          onMouseDown={() =>
            setSelected(
              null,
            )
          }
        >
          <section
            className="sa-dialog sa-dialog-wide"
            role="dialog"
            aria-modal="true"
            aria-label="Record details"
            onMouseDown={(
              event,
            ) =>
              event.stopPropagation()
            }
          >
            <div className="sa-dialog-head">
              <h2>
                {section ===
                "applications"
                  ? String(
                      selected.hotelName,
                    )
                  : "Record details"}
              </h2>

              <button
                onClick={() =>
                  setSelected(
                    null,
                  )
                }
                aria-label="Close details"
              >
                ×
              </button>
            </div>

            <RowDetails
              row={selected}
              section={section}
            />

            {section ===
              "applications" &&
              selected.status ===
                "PENDING_APPROVAL" && (
                <div className="sa-dialog-actions">
                  <button
                    className="sa-button sa-button-danger"
                    onClick={() => {
                      setDecision(
                        {
                          action:
                            "reject",
                          row: selected,
                        },
                      );

                      setNote("");
                    }}
                  >
                    Reject
                    application
                  </button>

                  <button
                    className="sa-button sa-button-primary"
                    onClick={() => {
                      setDecision(
                        {
                          action:
                            "approve",
                          row: selected,
                        },
                      );

                      setNote("");
                    }}
                  >
                    Approve &
                    onboard
                  </button>
                </div>
              )}

            {section ===
              "hotels" && (
              <div className="sa-dialog-actions">
                <button
                  className="sa-button sa-button-secondary"
                  onClick={() =>
                    setDecision(
                      {
                        action:
                          "hotel",
                        row: selected,
                        nextStatus:
                          selected.status ===
                          "ACTIVE"
                            ? "SUSPENDED"
                            : "ACTIVE",
                      },
                    )
                  }
                >
                  {selected.status ===
                  "ACTIVE"
                    ? "Suspend hotel"
                    : "Activate hotel"}
                </button>
              </div>
            )}

            {section ===
              "users" &&
              selected.role !==
                "ADMIN" && (
                <div className="sa-dialog-actions">
                  <button
                    className="sa-button sa-button-secondary"
                    onClick={() =>
                      setDecision(
                        {
                          action:
                            "user",
                          row: selected,
                          nextStatus:
                            selected.status ===
                            "ACTIVE"
                              ? "DISABLED"
                              : "ACTIVE",
                        },
                      )
                    }
                  >
                    {selected.status ===
                    "ACTIVE"
                      ? "Disable account"
                      : "Activate account"}
                  </button>
                </div>
              )}
          </section>
        </div>
      )}

      {decision && (
        <div className="sa-modal-backdrop sa-confirm-backdrop">
          <section
            className="sa-dialog"
            role="alertdialog"
            aria-modal="true"
            aria-label="Confirm admin action"
          >
            <div className="sa-dialog-head">
              <h2>
                Confirm
                administrator
                action
              </h2>

              <button
                onClick={() =>
                  setDecision(
                    null,
                  )
                }
                disabled={
                  working
                }
                aria-label="Close confirmation"
              >
                ×
              </button>
            </div>

            <p className="sa-confirm-copy">
              {decision.action ===
              "approve"
                ? `Approve ${String(
                    decision.row
                      .hotelName,
                  )}? This creates a hotel, changes the applicant to HOTEL_HEAD, and grants hotel access.`
                : decision.action ===
                    "reject"
                  ? `Reject ${String(
                      decision.row
                        .hotelName,
                    )}? The applicant will not receive hotel access.`
                  : decision.action ===
                      "backfill"
                    ? `Restore the complete hotel and room galleries for ${String(
                        decision.row
                          .hotelName,
                      )} from its original approved application? This does not create another hotel or change bookings or payments.`
                    : `Change ${
                        decision.action ===
                        "hotel"
                          ? "hotel"
                          : "user"
                      } status to ${decision.nextStatus}? This changes access or visibility on the live platform.`}
            </p>

            {(decision.action ===
              "approve" ||
              decision.action ===
                "reject") && (
              <label className="sa-note-label">
                {decision.action ===
                "reject"
                  ? "Rejection reason (required)"
                  : "Internal approval note (optional)"}

                <textarea
                  rows={3}
                  value={note}
                  onChange={(
                    event,
                  ) =>
                    setNote(
                      event.target
                        .value,
                    )
                  }
                  placeholder="Reason or internal review note"
                />
              </label>
            )}

            <div className="sa-dialog-actions">
              <button
                className="sa-button sa-button-secondary"
                onClick={() =>
                  setDecision(
                    null,
                  )
                }
                disabled={
                  working
                }
              >
                Cancel
              </button>

              <button
                className={
                  `sa-button ${
                    decision.action ===
                      "reject" ||
                    decision.nextStatus ===
                      "SUSPENDED" ||
                    decision.nextStatus ===
                      "DISABLED"
                      ? "sa-button-danger"
                      : "sa-button-primary"
                  }`
                }
                disabled={
                  working ||
                  (
                    decision.action ===
                      "reject" &&
                    !note.trim()
                  )
                }
                onClick={() =>
                  void confirm()
                }
              >
                {working
                  ? decision.action ===
                    "backfill"
                    ? "Restoring…"
                    : "Saving…"
                  : decision.action ===
                      "backfill"
                    ? "Restore gallery"
                    : "Confirm action"}
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}