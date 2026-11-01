import "server-only";
import { notFound } from "next/navigation";
import { cache } from "react";
import { z } from "zod";
import { ApiError, serverApi } from "@/shared/api/server";
import { offerSchema } from "./schemas";

export async function fetchOffers() {
  return (await serverApi("/campaign/offers", z.array(offerSchema))).data;
}

export const fetchOffer = cache(async (id: string) => {
  try {
    return (await serverApi(`/campaign/offers/${id}`, offerSchema)).data;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
});
