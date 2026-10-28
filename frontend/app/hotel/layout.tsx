import {HotelOpsProvider} from "@/components/hotel-ops/HotelOpsProvider";import {HotelOpsShell} from "@/components/hotel-ops/HotelOpsShell";
export default function HotelLayout({children}:{children:React.ReactNode}){return <HotelOpsProvider>{children}</HotelOpsProvider>}
