"use client";
/*
 * Documentation:
 * LoadingFileProgress — https://app.subframe.com/dd13c78ea6fd/library?component=LoadingFileProgress_af24f0a3-2fa7-46b0-abce-941567594f38
 */

import React from "react";
import * as SubframeUtils from "../utils";

interface LoadingFileProgressRootProps
  extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
}

const LoadingFileProgressRoot = React.forwardRef<
  HTMLElement,
  LoadingFileProgressRootProps
>(function LoadingFileProgressRoot(
  { className, ...otherProps }: LoadingFileProgressRootProps,
  ref
) {
  return (
    <div
      className={SubframeUtils.twClassNames(
        "flex w-full flex-col items-start gap-2 rounded-sm border border-solid border-neutral-border bg-default-background px-4 py-4",
        className
      )}
      ref={ref as any}
      {...otherProps}
    >
      <div className="flex w-full items-center justify-between">
        <div className="flex h-5 w-48 flex-none items-start rounded-sm bg-neutral-100 @keyframes pulse animate-pulse" />
        <div className="flex h-4 w-24 flex-none items-start rounded-sm bg-neutral-100 @keyframes pulse animate-pulse" />
      </div>
      <div className="flex h-2 w-full flex-none items-start rounded-sm bg-neutral-100 @keyframes pulse animate-pulse" />
    </div>
  );
});

export const LoadingFileProgress = LoadingFileProgressRoot;
