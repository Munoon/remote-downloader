"use client";
// @subframe/sync-disable
/*
 * Documentation:
 * Download prompt — https://app.subframe.com/dd13c78ea6fd/library?component=Download+prompt_87bbfa6b-0b08-4fc5-973a-8f4a75cc0365
 * Text Field — https://app.subframe.com/dd13c78ea6fd/library?component=Text+Field_be48ca43-f8e7-4c0e-8870-d219ea11abfe
 * Icon Button — https://app.subframe.com/dd13c78ea6fd/library?component=Icon+Button_af9405b1-8c54-4e01-9786-5aad308224f6
 * Button — https://app.subframe.com/dd13c78ea6fd/library?component=Button_3b777358-b86b-40af-9327-891efc6826fe
 */

import React from "react";
import * as SubframeUtils from "../utils";
import { TextField } from "./TextField";
import { Button } from "./Button";
import { FeatherDownload } from "@subframe/core";
import { FeatherCloud } from "@subframe/core";
import { FeatherTrash } from "@subframe/core";
import { IconButton } from "./IconButton";

interface DownloadPromptRootProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
  fileName: string;
  filePath: string;
  onFileNameChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onDownloadLocaly: (event: React.MouseEvent<HTMLButtonElement>) => void;
  onDownloadRemotely: (event: React.MouseEvent<HTMLButtonElement>) => void;
  onDelete: (event: React.MouseEvent<HTMLButtonElement>) => void;
  filePathElement: React.ReactNode;
  disabled: boolean;
}

const DownloadPromptRoot = React.forwardRef<
  HTMLElement,
  DownloadPromptRootProps
>(function DownloadPromptRoot(
  {
    className,
    fileName,
    filePath,
    onFileNameChange,
    onDownloadLocaly,
    onDownloadRemotely,
    onDelete,
    filePathElement,
    disabled,
    ...otherProps
  }: DownloadPromptRootProps,
  ref
) {
  return (
    <div
      className={SubframeUtils.twClassNames(
        "flex w-full flex-col items-start gap-3 rounded-md border border-solid border-neutral-border bg-neutral-50 px-4 py-4",
        className
      )}
      ref={ref as any}
      {...otherProps}
    >
      <div className="flex w-full flex-col items-start gap-2">
        <TextField
          className="h-auto w-full flex-none"
          label="File name"
          helpText=""
        >
          <TextField.Input value={fileName} onChange={onFileNameChange} disabled={disabled} />
        </TextField>

        <label
          className={SubframeUtils.twClassNames(
            "group/be48ca43 flex flex-col items-start gap-1 w-full",
            className
          )}
        >
          <span className="text-caption-bold font-caption-bold text-default-font">
            Location
          </span>
          <div className="w-full bg-default-background rounded-md">
            {filePathElement}
          </div>
        </label>

        
      </div>
      <div className="flex w-full items-center justify-between">
        <IconButton
          variant="destructive-tertiary"
          size="small"
          icon={<FeatherTrash />}
          onClick={onDelete}
          disabled={disabled}
          />
        <div className="flex items-center gap-2">
          <Button variant="neutral-tertiary" icon={<FeatherDownload />} onClick={onDownloadLocaly} disabled={disabled}>
            Download Localy
          </Button>
          <Button icon={<FeatherCloud />} onClick={onDownloadRemotely} disabled={disabled}>Download Remotely</Button>
        </div>
      </div>
    </div>
  );
});

export const DownloadPrompt = DownloadPromptRoot;
