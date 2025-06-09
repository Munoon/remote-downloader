// @subframe/sync-disable
// TODO fix component

"use client";
/*
 * Documentation:
 * Default Page Layout — https://app.subframe.com/dd13c78ea6fd/library?component=Default+Page+Layout_a57b1c43-310a-493f-b807-8cc88e2452cf
 */

import React from "react";
import * as SubframeUtils from "../utils";

interface DefaultPageLayoutRootProps
  extends React.HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode;
  className?: string;
}

const DefaultPageLayoutRoot = React.forwardRef<
  HTMLElement,
  DefaultPageLayoutRootProps
>(function DefaultPageLayoutRoot(
  { children, className, ...otherProps }: DefaultPageLayoutRootProps,
  ref
) {
  return (
    <div
      className={SubframeUtils.twClassNames(
        "flex h-full w-full items-start",
        className
      )}
      ref={ref as any}
      {...otherProps}
    >
      {children ? (
        <div className="flex grow shrink-0 basis-0 flex-col items-start gap-4 self-stretch overflow-y-auto bg-default-background">
          {children}
        </div>
      ) : null}
    </div>
  );
});

export const DefaultPageLayout = DefaultPageLayoutRoot;
