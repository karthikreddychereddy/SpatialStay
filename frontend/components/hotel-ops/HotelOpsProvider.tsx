"use client";
import {createContext,useCallback,useContext,useEffect,useMemo,useState} from "react";
import {usePathname,useRouter} from "next/navigation";
import {HotelOperator,hotelOpsApi} from "@/lib/hotel-operations-api";
type Ctx={operator:HotelOperator|null;hotelId:number|undefined;loading:boolean;setHotelId:(id:number)=>void;refresh:()=>Promise<void>;logout:()=>Promise<void>};
const C=createContext<Ctx|null>(null);
export function HotelOpsProvider({children}:{children:React.ReactNode}){const router=useRouter(),pathname=usePathname();const[operator,setOperator]=useState<HotelOperator|null>(null);const[hotelId,setHotelIdState]=useState<number>();const[loading,setLoading]=useState(true);
 const refresh=useCallback(async()=>{try{const u=await hotelOpsApi.me();setOperator(u);setHotelIdState(x=>x&&u.hotels.some(h=>h.id===x)?x:u.hotels[0]?.id)}catch{setOperator(null);if(pathname!=="/hotel/login")router.replace("/hotel/login")}finally{setLoading(false)}},[pathname,router]);
 useEffect(()=>{if(pathname!=="/hotel/login")void refresh();else setLoading(false)},[pathname,refresh]);
 const setHotelId=(id:number)=>{if(operator?.hotels.some(h=>h.id===id))setHotelIdState(id)};
 const logout=async()=>{await hotelOpsApi.logout();setOperator(null);router.replace("/hotel/login")};
 const v=useMemo(()=>({operator,hotelId,loading,setHotelId,refresh,logout}),[operator,hotelId,loading,refresh]);return <C.Provider value={v}>{children}</C.Provider>}
export function useHotelOps(){const c=useContext(C);if(!c)throw new Error("HotelOpsProvider missing");return c}
