"use client";

import {
  useCallback,
  useEffect,
  useState
} from "react";

import {
  AlertCircle,
  CheckCircle2,
  LogIn,
  LogOut,
  Search
} from "lucide-react";

import { HotelOpsShell } from "@/components/hotel-ops/HotelOpsShell";
import { useHotelOps } from "@/components/hotel-ops/HotelOpsProvider";

import {
  HotelOpsApiError,
  HotelReservation,
  hotelOpsApi
} from "@/lib/hotel-operations-api";

const views = [
  "ALL",
  "ARRIVALS",
  "DEPARTURES",
  "IN_HOUSE",
  "UPCOMING"
];

type Message = {
  type: "success" | "error";
  text: string;
} | null;

export default function Page() {
  const { hotelId } = useHotelOps();

  const [view, setView] = useState("ALL");
  const [search, setSearch] = useState("");
  const [rows, setRows] = useState<HotelReservation[]>([]);
  const [busy, setBusy] = useState("");
  const [message, setMessage] = useState<Message>(null);

  const load = useCallback(async () => {
    if (!hotelId) {
      return;
    }

    try {
      const reservations = await hotelOpsApi.reservations(
        hotelId,
        view,
        search
      );

      setRows(reservations);
    } catch (error) {
      if (error instanceof HotelOpsApiError) {
        setMessage({
          type: "error",
          text: error.message
        });
        return;
      }

      setMessage({
        type: "error",
        text: "Unable to load reservations."
      });
    }
  }, [hotelId, view, search]);

  useEffect(() => {
    void load();
  }, [load]);

  async function action(
    reservation: HotelReservation,
    type: "in" | "out"
  ) {
    if (!hotelId) {
      return;
    }

    setBusy(reservation.bookingId);
    setMessage(null);

    try {
      if (type === "in") {
        await hotelOpsApi.checkIn(
          reservation.bookingId,
          hotelId
        );

        setMessage({
          type: "success",
          text: `${reservation.bookingReference} checked in successfully.`
        });
      } else {
        await hotelOpsApi.checkOut(
          reservation.bookingId,
          hotelId
        );

        setMessage({
          type: "success",
          text: `${reservation.bookingReference} checked out successfully.`
        });
      }

      await load();
    } catch (error) {
      if (error instanceof HotelOpsApiError) {
        setMessage({
          type: "error",
          text: error.message
        });
      } else {
        setMessage({
          type: "error",
          text: "The reservation operation could not be completed."
        });
      }
    } finally {
      setBusy("");
    }
  }

  return (
    <HotelOpsShell>
      <div className="hotel-page-head">
        <div>
          <small>GUEST OPERATIONS</small>
          <h1>Reservations</h1>
          <p>
            Search stays, manage arrivals and complete check-outs.
          </p>
        </div>
      </div>

      {message && (
        <div
          role="alert"
          style={{
            display: "flex",
            alignItems: "center",
            gap: "10px",
            marginBottom: "18px",
            padding: "14px 16px",
            borderRadius: "14px",
            border: "1px solid currentColor"
          }}
        >
          {message.type === "success" ? (
            <CheckCircle2 size={20} />
          ) : (
            <AlertCircle size={20} />
          )}

          <span>{message.text}</span>

          <button
            type="button"
            onClick={() => setMessage(null)}
            aria-label="Dismiss message"
            style={{
              marginLeft: "auto",
              background: "transparent",
              border: 0,
              cursor: "pointer",
              fontSize: "18px"
            }}
          >
            ×
          </button>
        </div>
      )}

      <div className="hotel-toolbar">
        <div className="hotel-tabs">
          {views.map((item) => (
            <button
              key={item}
              className={item === view ? "active" : ""}
              onClick={() => {
                setView(item);
                setMessage(null);
              }}
            >
              {item.replace("_", " ")}
            </button>
          ))}
        </div>

        <div className="hotel-search">
          <Search />

          <input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setMessage(null);
            }}
            placeholder="Guest, reference, room…"
          />
        </div>
      </div>

      <section className="hotel-panel">
        <div className="hotel-table-wrap">
          <table className="hotel-table">
            <thead>
              <tr>
                <th>Reference / Guest</th>
                <th>Room</th>
                <th>Stay</th>
                <th>Guests</th>
                <th>Booking</th>
                <th>Stay status</th>
                <th>Action</th>
              </tr>
            </thead>

            <tbody>
              {rows.map((reservation) => (
                <tr key={reservation.bookingId}>
                  <td>
                    <b>{reservation.bookingReference}</b>

                    <small>
                      {reservation.guestName}
                      <br />
                      {reservation.guestEmail}
                    </small>
                  </td>

                  <td>
                    {reservation.roomName}

                    <small>
                      {reservation.roomType}
                    </small>
                  </td>

                  <td>
                    {reservation.checkIn}

                    <small>
                      to {reservation.checkOut}
                    </small>
                  </td>

                  <td>
                    {reservation.guests}
                  </td>

                  <td>
                    <span className="hotel-badge">
                      {reservation.bookingStatus}
                    </span>
                  </td>

                  <td>
                    <span className="hotel-badge soft">
                      {reservation.stayStatus.replaceAll("_", " ")}
                    </span>
                  </td>

                  <td>
                    {reservation.bookingStatus === "CONFIRMED" &&
                    reservation.stayStatus === "NOT_CHECKED_IN" ? (
                      <button
                        disabled={busy === reservation.bookingId}
                        className="hotel-action"
                        onClick={() => void action(reservation, "in")}
                      >
                        <LogIn />

                        {busy === reservation.bookingId
                          ? "Checking in…"
                          : "Check in"}
                      </button>
                    ) : reservation.stayStatus === "CHECKED_IN" ? (
                      <button
                        disabled={busy === reservation.bookingId}
                        className="hotel-action"
                        onClick={() => void action(reservation, "out")}
                      >
                        <LogOut />

                        {busy === reservation.bookingId
                          ? "Checking out…"
                          : "Check out"}
                      </button>
                    ) : (
                      <span className="hotel-muted">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {rows.length === 0 && (
            <div className="hotel-empty">
              No reservations match this view.
            </div>
          )}
        </div>
      </section>
    </HotelOpsShell>
  );
}