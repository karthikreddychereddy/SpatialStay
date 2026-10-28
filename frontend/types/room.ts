export type RoomReality = {
  verified: string[];
  guaranteed: string[];
  onRequest: string[];
  notGuaranteed: string[];
};

export type ReviewInsight = {
  label: string;
  score: number;
  sentiment: "positive" | "mixed";
};

export type Room = {
  id: string;
  hotelId: string;
  name: string;
  subtitle: string;
  rating: number;
  reviews: number;
  price: number;
  size: number;
  guests: number;
  beds: string;
  view: string;
  image: string;
  gallery: string[];
  amenities: string[];
  features: string[];
  available: number;
  refundable: boolean;
  breakfast: boolean;
  description: string;
  matchScore: number;
  matchReasons: string[];
  noiseLevel: string;
  workspace: string;
  bathroom: string;
  reality: RoomReality;
  reviewInsights: ReviewInsight[];
  reviewSummary: string;
};
