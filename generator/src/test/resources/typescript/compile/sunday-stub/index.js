function defineSchema(builder) {
  return {
    id: Symbol('test-schema'),
    build: builder,
  };
}

module.exports = {defineSchema};
