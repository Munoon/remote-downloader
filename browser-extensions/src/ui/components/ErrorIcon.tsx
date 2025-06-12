import { Tooltip } from "@subframe/core";
import { Tooltip as MessageTooltip } from "./Tooltip";
import { FeatherAlertCircle } from "@subframe/core";

export default function ErrorIcon({ text }: { text: string }) {
  return (
    <Tooltip.Provider>
      <Tooltip.Root delayDuration={0}>
        <Tooltip.Trigger>
          <FeatherAlertCircle className="text-body font-body text-error-600 inline" />
        </Tooltip.Trigger>
        <Tooltip.Portal>
          <Tooltip.Content
            side="top"
            align="center"
            sideOffset={4}
            asChild={true}
          >
            <MessageTooltip>{text}</MessageTooltip>
          </Tooltip.Content>
        </Tooltip.Portal>
      </Tooltip.Root>
    </Tooltip.Provider>
  );
}