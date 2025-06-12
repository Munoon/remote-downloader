"use client";
/*
 * Documentation:
 * NoDownloads — https://app.subframe.com/dd13c78ea6fd/library?component=NoDownloads_8130cee9-a172-4bba-a996-25cdb0b463ad
 * Icon with background — https://app.subframe.com/dd13c78ea6fd/library?component=Icon+with+background_c5d68c0e-4c0c-4cff-8d8c-6ff334859b3a
 */

import React from "react";
import * as SubframeUtils from "../utils";
import { IconWithBackground } from "./IconWithBackground";
import { FeatherDownload } from "@subframe/core";

interface NoDownloadsRootProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
}

const NoDownloadsRoot = React.forwardRef<HTMLElement, NoDownloadsRootProps>(
  function NoDownloadsRoot(
    { className, ...otherProps }: NoDownloadsRootProps,
    ref
  ) {
    return (
      <div
        className={SubframeUtils.twClassNames(
          "flex w-full flex-col items-center gap-4 rounded-sm border border-solid border-neutral-border px-4 py-4",
          className
        )}
        ref={ref as any}
        {...otherProps}
      >
        <div className="flex flex-col items-start gap-2">
          <IconWithBackground size="large" icon={<FeatherDownload />} />
        </div>
        <div className="flex flex-col items-center gap-1">
          <span className="text-heading-3 font-heading-3 text-default-font">
            No downloads yet
          </span>
          <span className="text-body font-body text-subtext-color">
            Start downloading files to see them appear here.
          </span>
        </div>
      </div>
    );
  }
);

export const NoDownloads = NoDownloadsRoot;
