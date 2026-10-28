"use client";

import {
  ChevronLeft,
  ChevronRight,
  Images,
  X,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

type PropertyGalleryProps = {
  images: string[];
  fallbackImage?: string | null;
  title: string;
  badge?: React.ReactNode;
  children?: React.ReactNode;
};

export function PropertyGallery({
  images,
  fallbackImage,
  title,
  badge,
  children,
}: PropertyGalleryProps) {
  const gallery = useMemo(() => {
    const values = [
      ...images,
      ...(fallbackImage ? [fallbackImage] : []),
    ]
      .map((image) => image?.trim())
      .filter(
        (image): image is string =>
          Boolean(image),
      );

    return Array.from(new Set(values));
  }, [images, fallbackImage]);

  const [selectedIndex, setSelectedIndex] =
    useState<number | null>(null);

  const selectedImage =
    selectedIndex !== null
      ? gallery[selectedIndex]
      : null;

  function previous() {
    if (selectedIndex === null) {
      return;
    }

    setSelectedIndex(
      selectedIndex === 0
        ? gallery.length - 1
        : selectedIndex - 1,
    );
  }

  function next() {
    if (selectedIndex === null) {
      return;
    }

    setSelectedIndex(
      selectedIndex === gallery.length - 1
        ? 0
        : selectedIndex + 1,
    );
  }

  useEffect(() => {
    if (selectedIndex === null) {
      return;
    }

    function handleKeyDown(
      event: KeyboardEvent,
    ) {
      if (event.key === "Escape") {
        setSelectedIndex(null);
      }

      if (
        event.key === "ArrowLeft" &&
        gallery.length > 1
      ) {
        setSelectedIndex((current) => {
          if (current === null) {
            return null;
          }

          return current === 0
            ? gallery.length - 1
            : current - 1;
        });
      }

      if (
        event.key === "ArrowRight" &&
        gallery.length > 1
      ) {
        setSelectedIndex((current) => {
          if (current === null) {
            return null;
          }

          return current ===
            gallery.length - 1
            ? 0
            : current + 1;
        });
      }
    }

    document.body.style.overflow =
      "hidden";

    window.addEventListener(
      "keydown",
      handleKeyDown,
    );

    return () => {
      document.body.style.overflow = "";

      window.removeEventListener(
        "keydown",
        handleKeyDown,
      );
    };
  }, [selectedIndex, gallery.length]);

  if (gallery.length === 0) {
    return (
      <div className="relative flex h-[300px] items-center justify-center overflow-hidden rounded-[22px] bg-gradient-to-br from-[#eaf3ff] to-[#dce9fb] sm:h-[460px]">
        <div className="text-center text-[#174f9f]">
          <Images
            size={42}
            className="mx-auto"
          />

          <p className="mt-3 text-sm font-bold">
            Photos coming soon
          </p>
        </div>

        {children}
      </div>
    );
  }

  const mainImage = gallery[0];

  const secondaryImages =
    gallery.slice(1, 4);

  return (
    <>
      <div className="relative">
        <div className="hidden h-[460px] grid-cols-[minmax(0,1.65fr)_minmax(260px,.75fr)] gap-2 overflow-hidden rounded-[22px] lg:grid">
          <button
            type="button"
            onClick={() =>
              setSelectedIndex(0)
            }
            className="group relative overflow-hidden bg-[#eaf3ff]"
            aria-label={`Open ${title} photo 1`}
          >
            <img
              src={mainImage}
              alt={`${title} photo 1`}
              className="h-full w-full object-cover transition duration-500 group-hover:scale-[1.02]"
            />

            <div className="absolute inset-0 bg-gradient-to-t from-[#061c45]/75 via-transparent to-transparent" />
          </button>

          <div className="grid min-h-0 grid-rows-2 gap-2">
            {secondaryImages
              .slice(0, 2)
              .map((image, index) => {
                const actualIndex =
                  index + 1;

                return (
                  <button
                    key={image}
                    type="button"
                    onClick={() =>
                      setSelectedIndex(
                        actualIndex,
                      )
                    }
                    className="group relative min-h-0 overflow-hidden bg-[#eaf3ff]"
                    aria-label={`Open ${title} photo ${
                      actualIndex + 1
                    }`}
                  >
                    <img
                      src={image}
                      alt={`${title} photo ${
                        actualIndex + 1
                      }`}
                      className="h-full w-full object-cover transition duration-500 group-hover:scale-[1.03]"
                    />

                    {index === 1 &&
                    gallery.length > 3 ? (
                      <div className="absolute inset-0 grid place-items-center bg-[#061c45]/55">
                        <span className="inline-flex items-center gap-2 rounded-full bg-white/95 px-4 py-2 text-sm font-extrabold text-[#173d70] shadow-lg">
                          <Images size={16} />
                          +{gallery.length - 3}{" "}
                          photos
                        </span>
                      </div>
                    ) : null}
                  </button>
                );
              })}

            {secondaryImages.length ===
            0 ? (
              <div className="grid place-items-center bg-gradient-to-br from-[#eaf3ff] to-[#dce9fb] text-[#56718f]">
                <div className="text-center">
                  <Images
                    size={28}
                    className="mx-auto"
                  />

                  <p className="mt-2 text-xs font-bold">
                    More photos coming soon
                  </p>
                </div>
              </div>
            ) : null}

            {secondaryImages.length ===
            1 ? (
              <button
                type="button"
                onClick={() =>
                  setSelectedIndex(0)
                }
                className="relative overflow-hidden bg-[#edf4ff]"
              >
                <img
                  src={mainImage}
                  alt=""
                  className="h-full w-full scale-110 object-cover opacity-20 blur-sm"
                />

                <div className="absolute inset-0 grid place-items-center">
                  <span className="inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 text-xs font-extrabold text-[#315276] shadow-sm">
                    <Images size={15} />
                    {gallery.length}{" "}
                    {gallery.length === 1
                      ? "photo"
                      : "photos"}
                  </span>
                </div>
              </button>
            ) : null}
          </div>
        </div>

        <div className="lg:hidden">
          <button
            type="button"
            onClick={() =>
              setSelectedIndex(0)
            }
            className="relative block h-[310px] w-full overflow-hidden rounded-[22px] bg-[#eaf3ff] sm:h-[420px]"
          >
            <img
              src={mainImage}
              alt={`${title} photo 1`}
              className="h-full w-full object-cover"
            />

            <div className="absolute inset-0 bg-gradient-to-t from-[#061c45]/75 via-transparent to-transparent" />
          </button>

          {gallery.length > 1 ? (
            <div className="hide-scrollbar mt-2 flex gap-2 overflow-x-auto pb-1">
              {gallery.map(
                (image, index) => (
                  <button
                    key={`${image}-${index}`}
                    type="button"
                    onClick={() =>
                      setSelectedIndex(index)
                    }
                    className="h-20 w-28 shrink-0 overflow-hidden rounded-xl border border-white/70 bg-[#eaf3ff]"
                  >
                    <img
                      src={image}
                      alt={`${title} photo ${
                        index + 1
                      }`}
                      className="h-full w-full object-cover"
                    />
                  </button>
                ),
              )}
            </div>
          ) : null}
        </div>

        <div className="pointer-events-none absolute bottom-5 left-5 right-5 z-10 flex items-end justify-between gap-4 text-white sm:bottom-7 sm:left-7 sm:right-7">
          <div className="pointer-events-auto">
            {badge}
          </div>

          {gallery.length > 1 ? (
            <button
              type="button"
              onClick={() =>
                setSelectedIndex(0)
              }
              className="pointer-events-auto inline-flex items-center gap-2 rounded-xl bg-white/95 px-4 py-2.5 text-xs font-extrabold text-[#173d70] shadow-lg backdrop-blur transition hover:bg-white"
            >
              <Images size={16} />
              View all {gallery.length} photos
            </button>
          ) : null}
        </div>

        {children}
      </div>

      {selectedImage ? (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-[#031126]/95 p-4 backdrop-blur-sm sm:p-8"
          role="dialog"
          aria-modal="true"
          aria-label={`${title} photo gallery`}
        >
          <button
            type="button"
            onClick={() =>
              setSelectedIndex(null)
            }
            className="absolute right-4 top-4 z-20 grid h-11 w-11 place-items-center rounded-full bg-white/10 text-white transition hover:bg-white/20 sm:right-7 sm:top-7"
            aria-label="Close gallery"
          >
            <X size={22} />
          </button>

          <div className="absolute left-4 top-4 rounded-full bg-white/10 px-4 py-2 text-xs font-bold text-white sm:left-7 sm:top-7">
            {(selectedIndex ?? 0) + 1} /{" "}
            {gallery.length}
          </div>

          {gallery.length > 1 ? (
            <button
              type="button"
              onClick={previous}
              className="absolute left-3 z-20 grid h-11 w-11 place-items-center rounded-full bg-white/10 text-white transition hover:bg-white/20 sm:left-7 sm:h-12 sm:w-12"
              aria-label="Previous photo"
            >
              <ChevronLeft size={25} />
            </button>
          ) : null}

          <img
            src={selectedImage}
            alt={`${title} photo ${
              (selectedIndex ?? 0) + 1
            }`}
            className="max-h-[82vh] max-w-[90vw] rounded-2xl object-contain shadow-2xl"
          />

          {gallery.length > 1 ? (
            <button
              type="button"
              onClick={next}
              className="absolute right-3 z-20 grid h-11 w-11 place-items-center rounded-full bg-white/10 text-white transition hover:bg-white/20 sm:right-7 sm:h-12 sm:w-12"
              aria-label="Next photo"
            >
              <ChevronRight size={25} />
            </button>
          ) : null}

          {gallery.length > 1 ? (
            <div className="hide-scrollbar absolute bottom-4 left-1/2 flex max-w-[85vw] -translate-x-1/2 gap-2 overflow-x-auto rounded-2xl bg-black/25 p-2 backdrop-blur sm:bottom-6">
              {gallery.map(
                (image, index) => (
                  <button
                    key={`${image}-${index}`}
                    type="button"
                    onClick={() =>
                      setSelectedIndex(index)
                    }
                    className={`h-14 w-20 shrink-0 overflow-hidden rounded-lg border-2 transition sm:h-16 sm:w-24 ${
                      selectedIndex === index
                        ? "border-white"
                        : "border-transparent opacity-60 hover:opacity-100"
                    }`}
                  >
                    <img
                      src={image}
                      alt=""
                      className="h-full w-full object-cover"
                    />
                  </button>
                ),
              )}
            </div>
          ) : null}
        </div>
      ) : null}
    </>
  );
}