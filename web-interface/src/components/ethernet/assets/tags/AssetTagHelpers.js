export const TAG_NAME_MAX_LENGTH = 24;
export const TAG_DESCRIPTION_MAX_LENGTH = 64;

export const cleanTagName = (name) => {
  return name.replace(/[^A-Za-z0-9_.-]/g, "").substring(0, TAG_NAME_MAX_LENGTH);
}

export const removeTagQuestion = (tag) => "Really take the tag \"" + tag.name + "\" off this asset?";
