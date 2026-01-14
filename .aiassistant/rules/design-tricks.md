---
apply: manually
---

1. Make your main action 3x more obvious than anything else on the screen.
   - Visual weight matters more than you think:
       - Your primary button should be impossible to miss
       - Secondary actions should be visible but not competing
       - Tertiary actions (like “Learn More”) should basically disappear
2. Letter Spacing on Headlines (Nobody Gets This Right)
   - Text under 40px: Leave tracking at 0
   - Text 40–70px: Set tracking to -1%
   - Text over 70px: Set tracking to -2% to -4% 
3. Font pairing: Stop using 5 different fonts. Use 2 maximum:
   * One for headlines (bold, attention-grabbing)
   * One for body text (readable, simple)
     * Inter (headlines) + Inter (body) — yes, same font works
     * Playfair Display (headlines) + Lato (body)
     * Montserrat (headlines) + Open Sans (body)
4. Rounded Corners That Actually Look Good
   * The formula:
     Inner radius = Outer radius - Gap between edges
     Example:
     - Card has 24px border radius (outer)
     - Image inside has 12px gap from edge
     - Image should have 12px border radius (24–12 = 12)
     - Exception: Pill-shaped buttons (50% border radius) ignore this rule. The shape is the point.
       - Pro move: Use iOS-style corner smoothing (called “squircles”) instead of true circles. Makes everything look polished. Figma has a plugin for this called “Superellipse.”
     * Spacing System (Or Your Design Looks Random)
       - The 8-point grid: All spacing should be multiples of 8: 8px, 16px, 24px, 32px, 40px, etc.
       - Example
         - 8px: Tight spacing (icon to text)
         -  16px: Default spacing (between UI elements)
         -  24px: Section spacing
         -  32px: Large section spacing
         -  48px: Hero section padding
         -  64px+: Major layout spacing

5. Color Picking (Without the Guesswork)
   Better method using HSB (Hue, Saturation, Brightness):
   Starting with a base color (like primary blue):
    - Darker shade:
        - Keep Hue the same
        - Increase Saturation by 10–20%
        - Decrease Brightness by 20–30%
   - Lighter shade:
      - Keep Hue the same
      - Decrease Saturation by 10–20%
        - Increase Brightness by 20–30%
   - Background colors: Never use pure black (#000000) or pure white (#FFFFFF).
       * Dark mode background: #0A0E27 (very dark blue)
       * Light mode background: #FAFBFC (very light gray-blue)
6. Card Design Without the Boring Layouts
   * Visual grouping instead of labels:
     -    Don’t write “Price: $99”
     -    Just show “$99” in large text with “per month” small beneath
     -    Users understand without labels
   * Creating depth without drop shadows:
     Shadows are overused. 
     -    Try this instead:
       * Take your background color
       * Increase brightness by 4–6 points
       * Decrease saturation by 10–20 points
       * Use that as your card color
7.  