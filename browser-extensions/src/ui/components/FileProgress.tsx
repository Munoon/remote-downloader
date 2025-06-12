"use client";
// @subframe/sync-disable
/*
 * Documentation:
 * FileProgress — https://app.subframe.com/dd13c78ea6fd/library?component=FileProgress_ed403c2d-2bdc-4345-a301-3f713ab99335
 * Badge — https://app.subframe.com/dd13c78ea6fd/library?component=Badge_97bdb082-1124-4dd7-a335-b14b822d0157
 * Icon Button — https://app.subframe.com/dd13c78ea6fd/library?component=Icon+Button_af9405b1-8c54-4e01-9786-5aad308224f6
 * Progress — https://app.subframe.com/dd13c78ea6fd/library?component=Progress_60964db0-a1bf-428b-b9d5-f34cdf58ea77
 */

import React from "react";
import * as SubframeUtils from "../utils";
import { Badge } from "./Badge";
import { IconButton } from "./IconButton";
import { FeatherPause } from "@subframe/core";
import { FeatherPlay } from "@subframe/core";
import { FeatherTrash } from "@subframe/core";
import { Progress } from "./Progress";
import ErrorIcon from "./ErrorIcon";

interface FileProgressRootProps extends React.HTMLAttributes<HTMLDivElement> {
  fileName?: React.ReactNode;
  progress?: number;
  subtitle?: React.ReactNode;
  variant?: "downloading" | "downloaded" | "paused";
  downloadSpeed?: React.ReactNode;
  className?: string;
  onDeleteHook: (e: React.MouseEvent<HTMLButtonElement>) => void;
  onPauseHook?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  onContinueHook?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  buttonsDisabled: boolean;
  errorMessage?: string;
}

const FileProgressRoot = React.forwardRef<HTMLElement, FileProgressRootProps>(
  function FileProgressRoot(
    {
      fileName,
      progress = 0,
      subtitle,
      variant,
      downloadSpeed,
      className,
      onDeleteHook,
      onPauseHook = (e) => e.preventDefault(),
      onContinueHook = (e) => e.preventDefault(),
      buttonsDisabled,
      errorMessage,
      ...otherProps
    }: FileProgressRootProps,
    ref
  ) {
    return (
      <div
        className={SubframeUtils.twClassNames(
          "group/ed403c2d flex w-full flex-col items-start gap-2 rounded-sm border border-solid border-neutral-border bg-default-background px-3 py-2",
          className
        )}
        ref={ref as any}
        {...otherProps}
      >
        <div className="flex w-full items-center justify-between">
          <span className="line-clamp-1 text-caption-bold font-caption-bold text-default-font">
            {fileName}
          </span>
          <div
            className={SubframeUtils.twClassNames("flex items-center gap-1", {
              flex: variant === "downloaded",
            })}
          >
            {errorMessage && <ErrorIcon text={errorMessage} />}

            {variant === 'downloading' && downloadSpeed && <Badge variant="neutral">{downloadSpeed}</Badge>}
            {variant === 'downloading' && <IconButton size="small" icon={<FeatherPause />} onClick={onPauseHook} disabled={buttonsDisabled} />}

            {variant === 'downloaded' && <Badge variant="success">Completed</Badge>}

            {variant === 'paused' && <Badge variant="neutral">Paused</Badge>}
            {variant === 'paused' && <IconButton size="small" icon={<FeatherPlay />} onClick={onContinueHook} disabled={buttonsDisabled} />}
            
            <IconButton
              variant="destructive-tertiary"
              size="small"
              onClick={onDeleteHook}
              disabled={buttonsDisabled}
              icon={<FeatherTrash />}
            />
          </div>
        </div>
        {(variant === 'downloading' || variant === 'paused') && <Progress value={progress} />}
        {subtitle && (
          <span className="text-caption font-caption text-subtext-color">
            {subtitle}
          </span>
        )}
      </div>
    );
  }
);

export const FileProgress = FileProgressRoot;
