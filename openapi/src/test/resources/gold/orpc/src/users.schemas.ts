import { z } from "zod";
import { userSchema } from "./schemas";

export const paginatedUsersSchema = z.object({
        "limit": z.number().int(),
        "page": z.number().int(),
        "total": z.number().int(),
        "users": z.array(userSchema)});
export type PaginatedUsers = z.infer<typeof paginatedUsersSchema>;

export const photoVariantSchema = z.object({
        "format": z.string(),
        "height": z.number().int(),
        "url": z.string(),
        "width": z.number().int()});
export type PhotoVariant = z.infer<typeof photoVariantSchema>;

export const updateUserRequestSchema = z.object({
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")});
export type UpdateUserRequest = z.infer<typeof updateUserRequestSchema>;

export const photoUploadSchema = z.object({
        "id": z.uuid(),
        "variants": z.record(z.string(), photoVariantSchema)});
export type PhotoUpload = z.infer<typeof photoUploadSchema>;
