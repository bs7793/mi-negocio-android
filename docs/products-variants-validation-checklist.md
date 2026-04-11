# Products + Variants validation checklist

This checklist covers functional validation and smoke checks for the first release of products with variants.

## Functional validation

- [ ] Home screen opens Products screen.
- [ ] Products list loads with empty state for new workspaces.
- [ ] Search by product name returns expected items.
- [ ] Search by SKU returns expected items.
- [ ] Product creation requires non-empty product name.
- [ ] Product creation requires at least one variant.
- [ ] Variant creation requires non-empty SKU and valid non-negative unit price.
- [ ] Initial inventory allows multiple warehouses per variant.
- [ ] Category selection is optional and persists in created rows.
- [ ] Product card displays total stock and variant summary.

## RLS and tenancy validation

- [ ] User A cannot read User B workspace products.
- [ ] User A cannot create products in User B workspace.
- [ ] User with member/editor role can create rows through RPC.
- [ ] User without active membership cannot read products RPC results.
- [ ] Option seed function creates rows only within target workspace.

## Data integrity validation

- [ ] Product name uniqueness is enforced per workspace.
- [ ] Variant SKU uniqueness is enforced per workspace.
- [ ] Variant inventory uniqueness is enforced per variant + warehouse.
- [ ] Variant option type assignment has one value per type per variant.
- [ ] Initial inventory movement row is written when quantity > 0.

## Basic performance checks

- [ ] `get_products_list` with 100 items responds in acceptable time for mobile UX.
- [ ] Query plan uses workspace and SKU/name indexes.
- [ ] Creating a product with 5 variants and 3 warehouses each completes in one transaction without timeouts.
- [ ] Repeated product list requests do not produce N+1 API patterns on Android.

