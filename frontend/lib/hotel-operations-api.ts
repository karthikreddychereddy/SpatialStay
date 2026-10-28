const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export type HotelRole = "HOTEL_STAFF" | "HOTEL_HEAD";
export type HotelAssignment = { id:number; slug:string; name:string; city:string; country:string };
export type HotelOperator = { userPublicId:string; email:string; firstName:string; lastName:string; role:HotelRole; hotels:HotelAssignment[] };
export type HotelDashboard = { hotel:{id:number;slug:string;name:string;city:string;country:string;status:string}; businessDate:string; todayArrivals:number; todayDepartures:number; currentlyStaying:number; upcomingReservations:number; totalRoomUnits:number; occupiedRoomUnits:number; heldRoomUnits:number; availableRoomUnits:number; confirmedBookingValue:number; currency:string; recentReservations:HotelReservation[] };
export type StayStatus="NOT_CHECKED_IN"|"CHECKED_IN"|"CHECKED_OUT";
export type HotelReservation={ bookingId:string; bookingPublicId?:string; bookingReference:string; bookingStatus:string; status?:string; stayStatus:StayStatus; guestName:string; guestEmail:string; roomName:string; roomType:string; checkIn:string; checkOut:string; guests:number; rooms:number; totalAmount:number; currency:string; confirmedAt:string|null; checkedInAt:string|null; checkedOutAt:string|null; createdAt?:string };
export type HotelRoom={roomId:number;hotelId:number;slug:string;name:string;roomType:string;maxGuests:number;totalUnits:number;pricePerNight:number;currency:string;status:string;spatialAvailable:boolean;updatedAt:string};
export type HotelProfile={id:number;slug:string;name:string;description:string|null;city:string;state:string|null;country:string;address:string|null;latitude:number|null;longitude:number|null;heroImageUrl:string|null;contactEmail:string|null;contactPhone:string|null;checkInTime:string|null;checkOutTime:string|null;checkInInstructions:string|null;amenities:string[];houseRules:string|null;childPolicy:string|null;petPolicy:string|null;smokingPolicy:string|null;verified:boolean;status:string;updatedAt:string};
export type HotelStaff={userId:string;email:string;firstName:string;lastName:string;accountStatus:string;assignmentActive:boolean;assignedAt:string;assignmentUpdatedAt:string};
export type FinancialOverview={hotelId:number;hotelName:string;from:string;to:string;currencies:Array<{currency:string;confirmedBookingValue:number;collectedPayments:number;succeededRefunds:number;netCollected:number;pendingRefundObligations:number;successfulPaymentCount:number;successfulRefundCount:number}>;recentTransactions:Array<{type:string;publicId:string;bookingPublicId:string;bookingReference:string;amount:number;currency:string;status:string;purpose:string;occurredAt:string}>};
export type AuditEvent={id:number;hotelId:number;actorPublicId:string;actorRole:string;action:string;resourceType:string;resourceId:string|null;summary:string|null;occurredAt:string};

type ApiErrorBody={message?:string;error?:string};
export class HotelOpsApiError extends Error { constructor(message:string, public status:number){super(message);this.name="HotelOpsApiError";} }
async function parseError(r:Response){let m=`Request failed (${r.status})`;try{const t=await r.text();if(t){try{const b=JSON.parse(t) as ApiErrorBody;m=b.message??b.error??t}catch{m=t}}}catch{}return new HotelOpsApiError(m,r.status)}
async function request<T>(path:string,init:RequestInit={}):Promise<T>{const r=await fetch(`${API_BASE_URL}${path}`,{...init,credentials:"include",headers:{Accept:"application/json",...init.headers}});if(!r.ok)throw await parseError(r);if(r.status===204)return undefined as T;return await r.json() as T}
async function csrf(){return request<{headerName:string;token:string}>("/api/auth/csrf")}
async function mutation<T>(path:string,method:"POST"|"PATCH"|"PUT",body?:unknown){const c=await csrf();return request<T>(path,{method,headers:{"Content-Type":"application/json",[c.headerName]:c.token},body:body===undefined?undefined:JSON.stringify(body)})}
const q=(hotelId?:number)=>hotelId?`?hotelId=${hotelId}`:"";
export const hotelOpsApi={
 login:(email:string,password:string)=>request<HotelOperator>("/api/hotel-operations/auth/login",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email,password})}),
 me:()=>request<HotelOperator>("/api/hotel-operations/auth/me"),
 logout:async()=>{const c=await csrf();return request<void>("/api/auth/logout",{method:"POST",headers:{[c.headerName]:c.token}})},
 dashboard:(id?:number)=>request<HotelDashboard>(`/api/hotel-operations/dashboard${q(id)}`),
 reservations:(id:number|undefined,view="ALL",search="")=>request<HotelReservation[]>(`/api/hotel-operations/reservations?${new URLSearchParams({...(id?{hotelId:String(id)}:{}),view,...(search?{search}:{})})}`),
 checkIn:(bookingId:string,id?:number)=>mutation<unknown>(`/api/hotel-operations/reservations/${bookingId}/check-in${q(id)}`,"POST"),
 checkOut:(bookingId:string,id?:number)=>mutation<unknown>(`/api/hotel-operations/reservations/${bookingId}/check-out${q(id)}`,"POST"),
 rooms:(id?:number)=>request<HotelRoom[]>(`/api/hotel-operations/rooms${q(id)}`),
 updateRoom:(roomId:number,id:number|undefined,body:{totalUnits?:number;pricePerNight?:number;currency?:string;status?:string})=>mutation<HotelRoom>(`/api/hotel-operations/rooms/${roomId}/commercial${q(id)}`,"PATCH",body),
 profile:(id?:number)=>request<HotelProfile>(`/api/hotel-operations/profile${q(id)}`),
 updateProfile:(id:number|undefined,body:Partial<HotelProfile>)=>mutation<HotelProfile>(`/api/hotel-operations/profile${q(id)}`,"PATCH",body),
 updateAmenities:(id:number|undefined,amenities:string[])=>mutation<HotelProfile>(`/api/hotel-operations/profile/amenities${q(id)}`,"PUT",{amenities}),
 updatePolicies:(id:number|undefined,body:Partial<HotelProfile>)=>mutation<HotelProfile>(`/api/hotel-operations/profile/policies${q(id)}`,"PATCH",body),
 staff:(id?:number)=>request<HotelStaff[]>(`/api/hotel-operations/staff${q(id)}`),
 createStaff:(id:number|undefined,body:{email:string;password:string;firstName:string;lastName:string})=>mutation<HotelStaff>(`/api/hotel-operations/staff${q(id)}`,"POST",body),
 setStaffActive:(userId:string,active:boolean,id?:number)=>mutation<HotelStaff>(`/api/hotel-operations/staff/${userId}/${active?"reactivate":"deactivate"}${q(id)}`,"POST"),
 financial:(id?:number)=>request<FinancialOverview>(`/api/hotel-operations/financial-overview${q(id)}`),
 audit:(id?:number)=>request<AuditEvent[]>(`/api/hotel-operations/audit${id?`?hotelId=${id}&limit=100`:"?limit=100"}`),
};
